package com.readinglog.app.agent.idempotency;

import com.readinglog.app.agent.envelope.ActionError;
import com.readinglog.app.agent.envelope.ActionException;
import com.readinglog.app.agent.envelope.ErrorCategory;
import com.readinglog.app.agent.envelope.FixHint;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * In-memory dedup store backing every mutating book action's idempotency key, per
 * .claude/skills/agentic-action-layer/contract/idempotency-contract.md. Keyed on
 * {@code (actionName, idempotencyKey)}; a replay with the same fingerprint (a stable string
 * derived from the call's other parameters) returns the original outcome — success or
 * failure — without re-executing. A replay with a different fingerprint is rejected as a
 * {@code conflict}, never silently executed.
 */
@Component
public class IdempotencyStore {

  private static final Duration TTL = Duration.ofHours(48);

  private final ConcurrentHashMap<String, Entry> records = new ConcurrentHashMap<>();
  private final Clock clock;

  public IdempotencyStore() {
    this(Clock.systemUTC());
  }

  IdempotencyStore(Clock clock) {
    this.clock = clock;
  }

  /**
   * Executes {@code operation} under the given action/key/fingerprint, replaying a prior
   * outcome instead of re-executing when the same key was already used with the same
   * fingerprint, and rejecting with a {@code conflict} error when the same key was used with
   * a different fingerprint.
   */
  public Object execute(String actionName, String idempotencyKey, String fingerprint, Supplier<Object> operation) {
    String storeKey = actionName + '\0' + idempotencyKey;
    Entry existing = records.compute(storeKey, (k, current) -> isLive(current) ? current : null);

    if (existing != null) {
      if (!existing.fingerprint.equals(fingerprint)) {
        throw new ActionException(
            ActionError.withFixHint(
                ErrorCategory.CONFLICT,
                "idempotency_key_reused",
                "idempotencyKey '" + idempotencyKey + "' was already used for a call with different parameters",
                false,
                new FixHint("idempotencyKey", "reused with different parameters", null)));
      }
      return existing.replay();
    }

    Instant expiresAt = clock.instant().plus(TTL);
    try {
      Object result = operation.get();
      records.put(storeKey, Entry.success(fingerprint, result, expiresAt));
      return result;
    } catch (ActionException e) {
      records.put(storeKey, Entry.failure(fingerprint, e.error(), expiresAt));
      throw e;
    }
  }

  private boolean isLive(Entry entry) {
    return entry != null && clock.instant().isBefore(entry.expiresAt);
  }

  private record Entry(String fingerprint, Object successValue, ActionError failure, Instant expiresAt) {

    static Entry success(String fingerprint, Object value, Instant expiresAt) {
      return new Entry(fingerprint, value, null, expiresAt);
    }

    static Entry failure(String fingerprint, ActionError error, Instant expiresAt) {
      return new Entry(fingerprint, null, error, expiresAt);
    }

    Object replay() {
      if (failure != null) {
        throw new ActionException(failure);
      }
      return successValue;
    }
  }
}
