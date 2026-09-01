package com.readinglog.app.agent.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.readinglog.app.agent.envelope.ActionException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GuardrailTokenStoreTest {

  private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
  private final GuardrailTokenStore store = new GuardrailTokenStore(clock);

  @Test
  @DisplayName("A token issued for a book confirms a matching delete exactly once")
  void issuedTokenConfirmsMatchingCallOnce() {
    String token = store.issue("delete_book", "42", "state-hash-a");

    store.consume(token, "delete_book", "42", "state-hash-a");

    assertThatThrownBy(() -> store.consume(token, "delete_book", "42", "state-hash-a"))
        .isInstanceOf(ActionException.class)
        .satisfies(
            e -> assertThat(((ActionException) e).error().code()).isEqualTo("confirmation_required"));
  }

  @Test
  @DisplayName("Consuming without a token is rejected as confirmation_required")
  void missingTokenIsRejected() {
    assertThatThrownBy(() -> store.consume(null, "delete_book", "42", "state-hash-a"))
        .isInstanceOf(ActionException.class)
        .satisfies(
            e -> assertThat(((ActionException) e).error().category().value()).isEqualTo("guardrail_blocked"));
  }

  @Test
  @DisplayName("An expired token is rejected")
  void expiredTokenIsRejected() {
    String token = store.issue("delete_book", "42", "state-hash-a");

    clock.advance(Duration.ofMinutes(6));

    assertThatThrownBy(() -> store.consume(token, "delete_book", "42", "state-hash-a"))
        .isInstanceOf(ActionException.class);
  }

  @Test
  @DisplayName("A token issued for one book cannot confirm a delete of a different book")
  void tokenDoesNotCarryOverToADifferentBook() {
    String token = store.issue("delete_book", "42", "state-hash-a");

    assertThatThrownBy(() -> store.consume(token, "delete_book", "43", "state-hash-b"))
        .isInstanceOf(ActionException.class);
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    MutableClock(Instant instant) {
      this.instant = instant;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
