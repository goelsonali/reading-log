package com.readinglog.app.agent.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.readinglog.app.agent.envelope.ActionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdempotencyStoreTest {

  private final IdempotencyStore store = new IdempotencyStore();

  @Test
  @DisplayName("First call executes the operation and stores its result")
  void firstCallExecutes() {
    AtomicInteger calls = new AtomicInteger();

    Object result = store.execute("create_book", "key-1", "fingerprint-a", () -> {
      calls.incrementAndGet();
      return "created";
    });

    assertThat(result).isEqualTo("created");
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("Replay with the same key and fingerprint returns the original result without re-executing")
  void replayWithSameFingerprintReplaysWithoutReexecuting() {
    AtomicInteger calls = new AtomicInteger();
    Supplier<Object> operation = () -> {
      calls.incrementAndGet();
      return "created-" + calls.get();
    };

    Object first = store.execute("create_book", "key-2", "fingerprint-a", operation);
    Object second = store.execute("create_book", "key-2", "fingerprint-a", operation);

    assertThat(first).isEqualTo("created-1");
    assertThat(second).isEqualTo("created-1");
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("Replay with the same key but a different fingerprint is rejected as a conflict")
  void replayWithDifferentFingerprintIsRejected() {
    store.execute("create_book", "key-3", "fingerprint-a", () -> "created");

    assertThatThrownBy(() -> store.execute("create_book", "key-3", "fingerprint-b", () -> "created-again"))
        .isInstanceOf(ActionException.class)
        .satisfies(
            e -> {
              ActionException ex = (ActionException) e;
              assertThat(ex.error().category().value()).isEqualTo("conflict");
              assertThat(ex.error().code()).isEqualTo("idempotency_key_reused");
              assertThat(ex.error().retryable()).isFalse();
            });
  }
}
