package com.readinglog.app.agent.guardrail;

import com.readinglog.app.agent.envelope.ActionError;
import com.readinglog.app.agent.envelope.ActionException;
import com.readinglog.app.agent.envelope.ErrorCategory;
import com.readinglog.app.agent.envelope.FixHint;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory confirmation-token store backing the {@code delete_book} guardrail, per
 * .claude/skills/agentic-action-layer/contract/guardrail-contract.md. A token issued by a
 * dry-run is bound to the exact resource previewed (here: the book id and a fingerprint of
 * its current state), expires after a short window, and is single-use — consuming it (valid
 * or not) removes it from the store, so a token can back at most one real call attempt.
 */
@Component
public class GuardrailTokenStore {

  private static final Duration TTL = Duration.ofMinutes(5);

  private final ConcurrentHashMap<String, Token> tokens = new ConcurrentHashMap<>();
  private final Clock clock;

  public GuardrailTokenStore() {
    this(Clock.systemUTC());
  }

  GuardrailTokenStore(Clock clock) {
    this.clock = clock;
  }

  /** Issues a new confirmation token bound to {@code (actionName, resourceKey, stateFingerprint)}. */
  public String issue(String actionName, String resourceKey, String stateFingerprint) {
    String token = UUID.randomUUID().toString();
    tokens.put(token, new Token(actionName, resourceKey, stateFingerprint, clock.instant().plus(TTL)));
    return token;
  }

  /**
   * Consumes a confirmation token, single-use. Throws a {@code guardrail_blocked} /
   * {@code confirmation_required} error if the token is missing, expired, or does not match
   * the exact action/resource/state it was issued for.
   */
  public void consume(String token, String actionName, String resourceKey, String stateFingerprint) {
    if (token == null) {
      throw confirmationRequired();
    }
    Token found = tokens.remove(token);
    if (found == null
        || clock.instant().isAfter(found.expiresAt)
        || !found.actionName.equals(actionName)
        || !found.resourceKey.equals(resourceKey)
        || !found.stateFingerprint.equals(stateFingerprint)) {
      throw confirmationRequired();
    }
  }

  private static ActionException confirmationRequired() {
    return new ActionException(
        ActionError.withFixHint(
            ErrorCategory.GUARDRAIL_BLOCKED,
            "confirmation_required",
            "This action is consequential and requires a confirmed dry-run before it can execute",
            true,
            new FixHint("confirmationToken", "missing, expired, or does not match a prior dry-run", null)));
  }

  private record Token(String actionName, String resourceKey, String stateFingerprint, Instant expiresAt) {}
}
