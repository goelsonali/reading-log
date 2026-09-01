package com.readinglog.app.agent.envelope;

/**
 * Matches error-envelope.schema.json's {@code error} object exactly. Every failure a wrapped
 * book action can produce is mapped to one of these before the action is considered wrapped
 * (agentic-action-layer constitution.md, Article 2) — {@code retryable} is always set
 * explicitly, never left for the caller to infer.
 */
public record ActionError(
    ErrorCategory category,
    String code,
    String message,
    boolean retryable,
    FixHint fixHint,
    Integer retryAfterMs,
    String originalStatus) {

  public static ActionError of(ErrorCategory category, String code, String message, boolean retryable) {
    return new ActionError(category, code, message, retryable, null, null, null);
  }

  public static ActionError withFixHint(
      ErrorCategory category, String code, String message, boolean retryable, FixHint fixHint) {
    return new ActionError(category, code, message, retryable, fixHint, null, null);
  }
}
