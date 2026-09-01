package com.readinglog.app.agent.action;

import com.readinglog.app.agent.envelope.ActionError;
import com.readinglog.app.agent.envelope.ActionException;
import com.readinglog.app.agent.envelope.ErrorCategory;
import com.readinglog.app.agent.envelope.FixHint;
import com.readinglog.app.agent.guardrail.GuardrailTokenStore;
import com.readinglog.app.agent.idempotency.IdempotencyStore;
import com.readinglog.app.dto.BookRequest;
import com.readinglog.app.dto.BookResponse;
import com.readinglog.app.exception.BookExportException;
import com.readinglog.app.exception.BookNotFoundException;
import com.readinglog.app.service.BookService;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Wraps {@link BookService} with the Agentic Action Layer contracts (error envelope,
 * idempotency, and — for {@code delete_book} — the dry-run/confirmation guardrail). Does not
 * modify {@link BookService} or {@code BookController}; this is a layer in front of them, per
 * openspec/changes/add-agentic-action-layer-books/design.md.
 *
 * <p>Every method returns a {@code Map<String, Object>} shaped exactly like the corresponding
 * action's {@code outputSchema} in {@code src/main/resources/agent/books-manifest.json}, or
 * throws {@link ActionException} carrying an {@link ActionError} shaped like this action's
 * entries in that manifest's {@code errors} list.
 */
@Service
public class BookAgentActions {

  private final BookService bookService;
  private final IdempotencyStore idempotencyStore;
  private final GuardrailTokenStore guardrailTokenStore;

  public BookAgentActions(
      BookService bookService, IdempotencyStore idempotencyStore, GuardrailTokenStore guardrailTokenStore) {
    this.bookService = bookService;
    this.idempotencyStore = idempotencyStore;
    this.guardrailTokenStore = guardrailTokenStore;
  }

  public Map<String, Object> listBooks() {
    List<Map<String, Object>> books = bookService.findAll().stream().map(BookAgentActions::toBookMap).toList();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("books", books);
    return result;
  }

  public Map<String, Object> getBook(Long id) {
    return toBookMap(fetchOrThrow(id));
  }

  public Map<String, Object> createBook(String bookName, String author, String review, String idempotencyKey) {
    requireNonBlank(bookName, "bookName", "book_name_required", "bookName is required");
    requireNonBlank(author, "author", "author_required", "author is required");

    String fingerprint = String.join("", bookName, author, review == null ? "" : review);
    @SuppressWarnings("unchecked")
    Map<String, Object> result =
        (Map<String, Object>)
            idempotencyStore.execute(
                "create_book",
                idempotencyKey,
                fingerprint,
                () -> toBookMap(bookService.create(new BookRequest(bookName, author, review))));
    return result;
  }

  public Map<String, Object> updateBook(
      Long id, String bookName, String author, String review, String idempotencyKey) {
    requireNonBlank(bookName, "bookName", "book_name_required", "bookName is required");
    requireNonBlank(author, "author", "author_required", "author is required");

    String fingerprint = String.join("", id.toString(), bookName, author, review == null ? "" : review);
    @SuppressWarnings("unchecked")
    Map<String, Object> result =
        (Map<String, Object>)
            idempotencyStore.execute(
                "update_book",
                idempotencyKey,
                fingerprint,
                () -> {
                  try {
                    return toBookMap(bookService.update(id, new BookRequest(bookName, author, review)));
                  } catch (BookNotFoundException e) {
                    throw notFound(id);
                  }
                });
    return result;
  }

  public Map<String, Object> deleteBook(Long id, String idempotencyKey, boolean dryRun, String confirmationToken) {
    if (dryRun) {
      BookResponse book = fetchOrThrow(id);
      String token = guardrailTokenStore.issue("delete_book", id.toString(), stateFingerprint(book));

      Map<String, Object> impact = new LinkedHashMap<>();
      impact.put(
          "summary",
          "Permanently delete '" + book.bookName() + "' by " + book.author() + ". This cannot be undone.");
      impact.put("reversible", false);
      impact.put("affectsThirdParty", false);

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("dryRun", true);
      result.put("preview", toBookMap(book));
      result.put("impact", impact);
      result.put("confirmationToken", token);
      return result;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> result =
        (Map<String, Object>)
            idempotencyStore.execute(
                "delete_book",
                idempotencyKey,
                id.toString(),
                () -> {
                  BookResponse book = fetchOrThrow(id);
                  guardrailTokenStore.consume(confirmationToken, "delete_book", id.toString(), stateFingerprint(book));
                  bookService.delete(id);

                  Map<String, Object> deleted = new LinkedHashMap<>();
                  deleted.put("deleted", true);
                  deleted.put("id", id);
                  return deleted;
                });
    return result;
  }

  public Map<String, Object> exportBooks(String idempotencyKey) {
    @SuppressWarnings("unchecked")
    Map<String, Object> result =
        (Map<String, Object>)
            idempotencyStore.execute(
                "export_books",
                idempotencyKey,
                "export",
                () -> {
                  try {
                    Path path = bookService.exportToJson();
                    Map<String, Object> exported = new LinkedHashMap<>();
                    exported.put("path", path.toString());
                    return exported;
                  } catch (BookExportException e) {
                    throw new ActionException(
                        ActionError.of(
                            ErrorCategory.SERVER_ERROR, "export_write_failed", e.getMessage(), true));
                  }
                });
    return result;
  }

  private BookResponse fetchOrThrow(Long id) {
    try {
      return bookService.findById(id);
    } catch (BookNotFoundException e) {
      throw notFound(id);
    }
  }

  private static ActionException notFound(Long id) {
    return new ActionException(
        ActionError.of(ErrorCategory.NOT_FOUND, "book_not_found", "No book found with id: " + id, false));
  }

  private static void requireNonBlank(String value, String field, String code, String message) {
    if (value == null || value.isBlank()) {
      throw new ActionException(
          ActionError.withFixHint(
              ErrorCategory.VALIDATION, code, message, true, new FixHint(field, "must not be blank", null)));
    }
  }

  private static String stateFingerprint(BookResponse book) {
    return String.join(
        "", book.bookName(), book.author(), book.review() == null ? "" : book.review());
  }

  private static Map<String, Object> toBookMap(BookResponse book) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", book.id());
    map.put("bookName", book.bookName());
    map.put("author", book.author());
    map.put("review", book.review());
    return map;
  }
}
