package com.readinglog.app.agent.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readinglog.app.agent.envelope.ActionException;
import com.readinglog.app.agent.guardrail.GuardrailTokenStore;
import com.readinglog.app.agent.idempotency.IdempotencyStore;
import com.readinglog.app.domain.Book;
import com.readinglog.app.repository.BookRepository;
import com.readinglog.app.service.BookService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookAgentActionsTest {

  @Mock private BookRepository bookRepository;

  private BookAgentActions actions;

  @BeforeEach
  void setUp() {
    BookService bookService = new BookService(bookRepository, new ObjectMapper());
    actions = new BookAgentActions(bookService, new IdempotencyStore(), new GuardrailTokenStore());
  }

  // --- list_books / get_book (read-only) ---

  @Test
  @DisplayName("list_books returns every book")
  void listBooksReturnsAllBooks() {
    when(bookRepository.findAll())
        .thenReturn(List.of(Book.builder().id(1L).bookName("Clean Code").author("Robert C. Martin").build()));

    Map<String, Object> result = actions.listBooks();

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> books = (List<Map<String, Object>>) result.get("books");
    assertThat(books).hasSize(1);
    assertThat(books.get(0).get("bookName")).isEqualTo("Clean Code");
  }

  @Test
  @DisplayName("get_book returns the book by id")
  void getBookReturnsBookById() {
    when(bookRepository.findById(1L))
        .thenReturn(Optional.of(Book.builder().id(1L).bookName("Clean Code").author("Robert C. Martin").build()));

    Map<String, Object> result = actions.getBook(1L);

    assertThat(result.get("bookName")).isEqualTo("Clean Code");
  }

  @Test
  @DisplayName("get_book on an unknown id fails with book_not_found")
  void getBookOnUnknownIdFailsNotFound() {
    when(bookRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> actions.getBook(99L))
        .isInstanceOf(ActionException.class)
        .satisfies(e -> assertThat(((ActionException) e).error().code()).isEqualTo("book_not_found"));
  }

  // --- create_book / update_book ---

  @Test
  @DisplayName("create_book creates a book and replays on a retried idempotency key")
  void createBookCreatesAndReplays() {
    when(bookRepository.save(any(Book.class)))
        .thenReturn(Book.builder().id(1L).bookName("Dune").author("Frank Herbert").build());

    Map<String, Object> first = actions.createBook("Dune", "Frank Herbert", null, "key-1");
    Map<String, Object> second = actions.createBook("Dune", "Frank Herbert", null, "key-1");

    assertThat(first.get("id")).isEqualTo(1L);
    assertThat(second).isEqualTo(first);
    org.mockito.Mockito.verify(bookRepository, org.mockito.Mockito.times(1)).save(any(Book.class));
  }

  @Test
  @DisplayName("create_book with a blank bookName fails validation with a fix hint")
  void createBookBlankNameFailsValidation() {
    assertThatThrownBy(() -> actions.createBook(" ", "Frank Herbert", null, "key-2"))
        .isInstanceOf(ActionException.class)
        .satisfies(
            e -> {
              ActionException ex = (ActionException) e;
              assertThat(ex.error().code()).isEqualTo("book_name_required");
              assertThat(ex.error().fixHint().field()).isEqualTo("bookName");
            });
  }

  @Test
  @DisplayName("create_book with a blank author fails validation with a fix hint")
  void createBookBlankAuthorFailsValidation() {
    assertThatThrownBy(() -> actions.createBook("Dune", "", null, "key-3"))
        .isInstanceOf(ActionException.class)
        .satisfies(e -> assertThat(((ActionException) e).error().code()).isEqualTo("author_required"));
  }

  @Test
  @DisplayName("update_book updates an existing book")
  void updateBookUpdatesExisting() {
    when(bookRepository.findById(1L))
        .thenReturn(Optional.of(Book.builder().id(1L).bookName("Dune").author("Frank Herbert").build()));
    when(bookRepository.save(any(Book.class)))
        .thenReturn(Book.builder().id(1L).bookName("Dune Messiah").author("Frank Herbert").build());

    Map<String, Object> result = actions.updateBook(1L, "Dune Messiah", "Frank Herbert", null, "key-4");

    assertThat(result.get("bookName")).isEqualTo("Dune Messiah");
  }

  @Test
  @DisplayName("update_book on an unknown id fails with book_not_found")
  void updateBookOnUnknownIdFailsNotFound() {
    when(bookRepository.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> actions.updateBook(99L, "Dune", "Frank Herbert", null, "key-5"))
        .isInstanceOf(ActionException.class)
        .satisfies(e -> assertThat(((ActionException) e).error().code()).isEqualTo("book_not_found"));
  }

  // --- delete_book (guardrail) ---

  @Test
  @DisplayName("delete_book for real without a confirmation token is guardrail_blocked")
  void deleteBookWithoutTokenIsBlocked() {
    when(bookRepository.findById(1L))
        .thenReturn(Optional.of(Book.builder().id(1L).bookName("Dune").author("Frank Herbert").build()));

    assertThatThrownBy(() -> actions.deleteBook(1L, "key-6", false, null))
        .isInstanceOf(ActionException.class)
        .satisfies(
            e -> assertThat(((ActionException) e).error().category().value()).isEqualTo("guardrail_blocked"));
    org.mockito.Mockito.verify(bookRepository, org.mockito.Mockito.never()).delete(any(Book.class));
  }

  @Test
  @DisplayName("delete_book dry-run previews the book and issues a confirmation token without deleting")
  void deleteBookDryRunPreviewsWithoutDeleting() {
    when(bookRepository.findById(1L))
        .thenReturn(Optional.of(Book.builder().id(1L).bookName("Dune").author("Frank Herbert").build()));

    Map<String, Object> result = actions.deleteBook(1L, "key-7", true, null);

    assertThat(result.get("dryRun")).isEqualTo(true);
    assertThat(result.get("confirmationToken")).isNotNull();
    org.mockito.Mockito.verify(bookRepository, org.mockito.Mockito.never()).delete(any(Book.class));
  }

  @Test
  @DisplayName("delete_book confirmed with a valid token deletes the book")
  void deleteBookConfirmedWithValidTokenDeletes() {
    Book book = Book.builder().id(1L).bookName("Dune").author("Frank Herbert").build();
    when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

    Map<String, Object> preview = actions.deleteBook(1L, "key-preview", true, null);
    String token = (String) preview.get("confirmationToken");

    Map<String, Object> result = actions.deleteBook(1L, "key-8", false, token);

    assertThat(result.get("deleted")).isEqualTo(true);
    org.mockito.Mockito.verify(bookRepository).delete(book);
  }

  @Test
  @DisplayName("a confirmation token for one book cannot confirm a delete of a different book")
  void tokenDoesNotCarryOverToADifferentBook() {
    when(bookRepository.findById(1L))
        .thenReturn(Optional.of(Book.builder().id(1L).bookName("Dune").author("Frank Herbert").build()));
    when(bookRepository.findById(2L))
        .thenReturn(Optional.of(Book.builder().id(2L).bookName("Foundation").author("Isaac Asimov").build()));

    Map<String, Object> preview = actions.deleteBook(1L, "key-preview-2", true, null);
    String token = (String) preview.get("confirmationToken");

    assertThatThrownBy(() -> actions.deleteBook(2L, "key-9", false, token)).isInstanceOf(ActionException.class);
    org.mockito.Mockito.verify(bookRepository, org.mockito.Mockito.never()).delete(any(Book.class));
  }

  // --- export_books ---

  @Test
  @DisplayName("export_books writes the export file and reports its path")
  void exportBooksWritesFile() throws IOException {
    when(bookRepository.findAll()).thenReturn(List.of());

    Map<String, Object> result = actions.exportBooks("key-10");

    assertThat(result.get("path")).isNotNull();
    java.nio.file.Files.deleteIfExists(Path.of((String) result.get("path")));
  }

  @Test
  @DisplayName("export_books maps a write failure to export_write_failed")
  void exportBooksMapsWriteFailure() throws IOException {
    ObjectMapper failingMapper = org.mockito.Mockito.mock(ObjectMapper.class);
    doThrow(new IOException("disk full")).when(failingMapper).writeValue(any(File.class), any());
    when(bookRepository.findAll()).thenReturn(List.of());
    BookService failingService = new BookService(bookRepository, failingMapper);
    BookAgentActions failingActions =
        new BookAgentActions(failingService, new IdempotencyStore(), new GuardrailTokenStore());

    assertThatThrownBy(() -> failingActions.exportBooks("key-11"))
        .isInstanceOf(ActionException.class)
        .satisfies(e -> assertThat(((ActionException) e).error().code()).isEqualTo("export_write_failed"));
  }
}
