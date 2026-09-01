package com.readinglog.app.agent.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentManifestControllerIntegrationTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Test
  @DisplayName(
      "Given the manifest resource, when GET /api/v1/agent/books/manifest is called, then it"
          + " returns the exact same manifest content")
  void manifestEndpointMatchesManifestResource() throws Exception {
    ResponseEntity<JsonNode> response =
        restTemplate.getForEntity(url("/api/v1/agent/books/manifest"), JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode expected = new ObjectMapper().readTree(new ClassPathResource("agent/books-manifest.json").getInputStream());
    assertThat(response.getBody()).isEqualTo(expected);

    List<String> names = new java.util.ArrayList<>();
    response.getBody().get("actions").forEach(a -> names.add(a.get("name").asText()));
    assertThat(names)
        .containsExactlyInAnyOrder(
            "list_books", "get_book", "create_book", "update_book", "delete_book", "export_books");
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }
}
