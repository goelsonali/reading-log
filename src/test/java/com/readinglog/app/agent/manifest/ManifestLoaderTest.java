package com.readinglog.app.agent.manifest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ManifestLoaderTest {

  private final ManifestLoader loader = new ManifestLoader(new ObjectMapper());

  @Test
  @DisplayName("Given the books manifest, when loaded, then all 6 book actions are present")
  void shouldContainAllSixActions() {
    List<String> names = loader.actions().stream().map(a -> a.get("name").asText()).toList();

    assertThat(names)
        .containsExactlyInAnyOrder(
            "list_books", "get_book", "create_book", "update_book", "delete_book", "export_books");
  }

  @Test
  @DisplayName(
      "Given a consequential action, when inspected, then requiresDryRun and costTier are set")
  void consequentialActionsMustDeclareDryRunAndCostTier() {
    for (JsonNode action : loader.actions()) {
      if ("consequential".equals(action.get("sideEffects").asText())) {
        assertThat(action.get("requiresDryRun").asBoolean())
            .as("requiresDryRun for %s", action.get("name").asText())
            .isTrue();
        assertThat(action.hasNonNull("costTier"))
            .as("costTier for %s", action.get("name").asText())
            .isTrue();
      }
    }
  }

  @Test
  @DisplayName("Given delete_book, when inspected, then it is the only consequential action")
  void deleteBookIsTheOnlyConsequentialAction() {
    List<String> consequential =
        loader.actions().stream()
            .filter(a -> "consequential".equals(a.get("sideEffects").asText()))
            .map(a -> a.get("name").asText())
            .toList();

    assertThat(consequential).containsExactly("delete_book");
  }

  @Test
  @DisplayName("Given an action name, when its input schema is requested, then it is valid JSON")
  void inputSchemaJsonIsSerializable() {
    assertThat(loader.inputSchemaJson("create_book")).contains("bookName").contains("idempotencyKey");
  }
}
