package com.readinglog.app.agent.envelope;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Coarse failure bucket an agent can branch on without parsing prose. Matches
 * error-envelope.schema.json's {@code category} enum exactly.
 */
public enum ErrorCategory {
  VALIDATION("validation"),
  AUTH("auth"),
  NOT_FOUND("not_found"),
  CONFLICT("conflict"),
  RATE_LIMITED("rate_limited"),
  GUARDRAIL_BLOCKED("guardrail_blocked"),
  UPSTREAM_UNAVAILABLE("upstream_unavailable"),
  SERVER_ERROR("server_error");

  private final String value;

  ErrorCategory(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }
}
