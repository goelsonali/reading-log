package com.readinglog.app.agent.envelope;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * One assertion per row of the error-mapping table in
 * openspec/changes/add-agentic-action-layer-books/design.md, Decision 3.
 */
class ActionErrorTest {

  @Test
  @DisplayName("book_not_found is not_found and not retryable")
  void bookNotFound() {
    ActionError error = ActionError.of(ErrorCategory.NOT_FOUND, "book_not_found", "No such book", false);

    assertThat(error.category()).isEqualTo(ErrorCategory.NOT_FOUND);
    assertThat(error.retryable()).isFalse();
    assertThat(error.fixHint()).isNull();
  }

  @Test
  @DisplayName("book_name_required is validation, retryable, and fixes bookName")
  void bookNameRequired() {
    ActionError error =
        ActionError.withFixHint(
            ErrorCategory.VALIDATION,
            "book_name_required",
            "bookName is required",
            true,
            new FixHint("bookName", "must not be blank", null));

    assertThat(error.category()).isEqualTo(ErrorCategory.VALIDATION);
    assertThat(error.retryable()).isTrue();
    assertThat(error.fixHint().field()).isEqualTo("bookName");
  }

  @Test
  @DisplayName("author_required is validation, retryable, and fixes author")
  void authorRequired() {
    ActionError error =
        ActionError.withFixHint(
            ErrorCategory.VALIDATION,
            "author_required",
            "author is required",
            true,
            new FixHint("author", "must not be blank", null));

    assertThat(error.category()).isEqualTo(ErrorCategory.VALIDATION);
    assertThat(error.retryable()).isTrue();
    assertThat(error.fixHint().field()).isEqualTo("author");
  }

  @Test
  @DisplayName("export_write_failed is server_error and retryable")
  void exportWriteFailed() {
    ActionError error =
        ActionError.of(ErrorCategory.SERVER_ERROR, "export_write_failed", "Failed to write export file", true);

    assertThat(error.category()).isEqualTo(ErrorCategory.SERVER_ERROR);
    assertThat(error.retryable()).isTrue();
  }

  @Test
  @DisplayName("idempotency_key_reused is conflict, not retryable, and fixes idempotencyKey")
  void idempotencyKeyReused() {
    ActionError error =
        ActionError.withFixHint(
            ErrorCategory.CONFLICT,
            "idempotency_key_reused",
            "idempotencyKey was already used with different parameters",
            false,
            new FixHint("idempotencyKey", "reused with different parameters", null));

    assertThat(error.category()).isEqualTo(ErrorCategory.CONFLICT);
    assertThat(error.retryable()).isFalse();
    assertThat(error.fixHint().field()).isEqualTo("idempotencyKey");
  }

  @Test
  @DisplayName("confirmation_required is guardrail_blocked, retryable, and fixes confirmationToken")
  void confirmationRequired() {
    ActionError error =
        ActionError.withFixHint(
            ErrorCategory.GUARDRAIL_BLOCKED,
            "confirmation_required",
            "A confirmed dry-run is required before this action can execute",
            true,
            new FixHint("confirmationToken", "missing or expired", null));

    assertThat(error.category()).isEqualTo(ErrorCategory.GUARDRAIL_BLOCKED);
    assertThat(error.retryable()).isTrue();
    assertThat(error.fixHint().field()).isEqualTo("confirmationToken");
  }

  @Test
  @DisplayName("unknown_error is the server_error fallback and not retryable")
  void unknownErrorFallback() {
    ActionError error = ActionError.of(ErrorCategory.SERVER_ERROR, "unknown_error", "Unexpected error", false);

    assertThat(error.category()).isEqualTo(ErrorCategory.SERVER_ERROR);
    assertThat(error.retryable()).isFalse();
  }

  @Test
  @DisplayName("ActionException carries its error and surfaces it as an ErrorEnvelope")
  void actionExceptionCarriesError() {
    ActionError error = ActionError.of(ErrorCategory.NOT_FOUND, "book_not_found", "No such book", false);
    ActionException exception = new ActionException(error);

    ErrorEnvelope envelope = ErrorEnvelope.of(exception.error());

    assertThat(envelope.ok()).isFalse();
    assertThat(envelope.error()).isEqualTo(error);
  }
}
