package com.readinglog.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readinglog.app.domain.Book;
import com.readinglog.app.dto.BookRequest;
import com.readinglog.app.dto.BookResponse;
import com.readinglog.app.exception.BookExportException;
import com.readinglog.app.exception.BookNotFoundException;
import com.readinglog.app.repository.BookRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

  @Mock private BookRepository bookRepository;

  @Mock private ObjectMapper objectMapper;

  @InjectMocks private BookService bookService;

  @TempDir Path tempDir;

  @Test
  @DisplayName(
      "Given a valid book request, when creating a book, then it should save and return the"
          + " book")
  void shouldCreateBook() {
    BookRequest request = new BookRequest("Clean Code", "Robert C. Martin", "Great read");
    Book saved =
        Book.builder()
            .id(1L)
            .bookName("Clean Code")
            .author("Robert C. Martin")
            .review("Great read")
            .build();
    when(bookRepository.save(org.mockito.ArgumentMatchers.any(Book.class))).thenReturn(saved);

    BookResponse response = bookService.create(request);

    assertThat(response.id()).isEqualTo(1L);
    assertThat(response.bookName()).isEqualTo("Clean Code");
  }

  @Test
  @DisplayName("Given existing books, when finding all, then it should return all books")
  void shouldFindAllBooks() {
    Book book = Book.builder().id(1L).bookName("Clean Code").author("Robert C. Martin").build();
    when(bookRepository.findAll()).thenReturn(List.of(book));

    List<BookResponse> responses = bookService.findAll();

    assertThat(responses).hasSize(1);
    assertThat(responses.get(0).bookName()).isEqualTo("Clean Code");
  }

  @Test
  @DisplayName("Given a non-existent id, when finding by id, then it should throw an exception")
  void shouldThrowWhenBookNotFound() {
    when(bookRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> bookService.findById(99L)).isInstanceOf(BookNotFoundException.class);
  }

  @Test
  @DisplayName("Given books, when exporting, then a JSON file is written with all book data")
  void shouldExportBooksToJsonFile() throws IOException {
    Book book =
        Book.builder()
            .id(1L)
            .bookName("Clean Code")
            .author("Robert C. Martin")
            .review("Great read")
            .build();
    when(bookRepository.findAll()).thenReturn(List.of(book));

    BookService realService = new BookService(bookRepository, new ObjectMapper());
    Path target = tempDir.resolve("books.json");
    Path exported = realService.exportToJson(target);

    assertThat(exported).isEqualTo(target);
    assertThat(Files.exists(target)).isTrue();
    String content = Files.readString(target);
    assertThat(content)
        .contains("\"id\":1")
        .contains("\"bookName\":\"Clean Code\"")
        .contains("\"author\":\"Robert C. Martin\"")
        .contains("\"review\":\"Great read\"");
  }

  @Test
  @DisplayName("Given no books, when exporting, then a valid empty JSON array is written")
  void shouldExportEmptyCollection() throws IOException {
    when(bookRepository.findAll()).thenReturn(List.of());

    BookService realService = new BookService(bookRepository, new ObjectMapper());
    Path target = tempDir.resolve("empty-books.json");
    realService.exportToJson(target);

    String content = Files.readString(target);
    assertThat(content.trim()).isEqualTo("[]");
  }

  @Test
  @DisplayName("Given a write failure, when exporting, then a runtime exception is thrown")
  void shouldThrowWhenExportWriteFails() throws IOException {
    Book book = Book.builder().id(1L).bookName("Clean Code").author("Robert C. Martin").build();
    when(bookRepository.findAll()).thenReturn(List.of(book));
    doThrow(new IOException("disk full"))
        .when(objectMapper)
        .writeValue(org.mockito.ArgumentMatchers.any(java.io.File.class), org.mockito.ArgumentMatchers.any());

    Path target = tempDir.resolve("books.json");
    assertThatThrownBy(() -> bookService.exportToJson(target))
        .isInstanceOf(BookExportException.class)
        .hasMessageContaining("Failed to export");
  }
}