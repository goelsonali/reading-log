package com.readinglog.app.agent.envelope;

/** Matches error-envelope.schema.json's top-level shape: {@code {"ok": false, "error": {...}}}. */
public record ErrorEnvelope(boolean ok, ActionError error) {

  public static ErrorEnvelope of(ActionError error) {
    return new ErrorEnvelope(false, error);
  }
}
