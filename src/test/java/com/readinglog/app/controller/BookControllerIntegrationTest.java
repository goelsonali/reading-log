package com.readinglog.app.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.readinglog.app.dto.BookRequest;
import com.readinglog.app.dto.BookResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookControllerIntegrationTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Test
  @DisplayName(
      "Given a valid book request, when posting to /api/v1/books, then it should create and"
          + " return the book")
  void shouldCreateAndFetchBook() {
    BookRequest request = new BookRequest("The Hobbit", "J.R.R. Tolkien", "A delightful read");

    ResponseEntity<BookResponse> createResponse =
        restTemplate.postForEntity(url("/api/v1/books"), request, BookResponse.class);

    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(createResponse.getBody()).isNotNull();
    assertThat(createResponse.getBody().bookName()).isEqualTo("The Hobbit");

    Long id = createResponse.getBody().id();
    ResponseEntity<BookResponse> getResponse =
        restTemplate.getForEntity(url("/api/v1/books/" + id), BookResponse.class);

    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(getResponse.getBody().author()).isEqualTo("J.R.R. Tolkien");
  }

  @Test
  @DisplayName(
      "Given books exist, when posting to /api/v1/books/export, then a books.json file is created"
          + " with the seeded entries")
  void shouldExportBooksToJsonFile() throws IOException {
    BookRequest request = new BookRequest("The Hobbit", "J.R.R. Tolkien", "A delightful read");
    restTemplate.postForEntity(url("/api/v1/books"), request, BookResponse.class);

    Path exportFile = Path.of("books.json");
    Files.deleteIfExists(exportFile);

    try {
      ResponseEntity<Void> exportResponse =
          restTemplate.exchange(
              url("/api/v1/books/export"), HttpMethod.POST, HttpEntity.EMPTY, Void.class);

      assertThat(exportResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(Files.exists(exportFile)).isTrue();
      String content = Files.readString(exportFile);
      assertThat(content)
          .contains("\"bookName\":\"The Hobbit\"")
          .contains("\"author\":\"J.R.R. Tolkien\"");
    } finally {
      Files.deleteIfExists(exportFile);
    }
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }
}
