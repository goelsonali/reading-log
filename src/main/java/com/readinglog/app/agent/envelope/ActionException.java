package com.readinglog.app.agent.envelope;

/**
 * Signals a mapped action failure. Carries the {@link ActionError} that the calling
 * interface (MCP tool handler, REST discovery endpoint) turns into an {@link ErrorEnvelope}
 * — the boundary never leaks a raw exception to an agent.
 */
public class ActionException extends RuntimeException {

  private final ActionError error;

  public ActionException(ActionError error) {
    super(error.message());
    this.error = error;
  }

  public ActionError error() {
    return error;
  }
}
