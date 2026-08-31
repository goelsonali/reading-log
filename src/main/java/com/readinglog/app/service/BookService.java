package com.readinglog.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readinglog.app.domain.Book;
import com.readinglog.app.dto.BookRequest;
import com.readinglog.app.dto.BookResponse;
import com.readinglog.app.exception.BookExportException;
import com.readinglog.app.exception.BookNotFoundException;
import com.readinglog.app.repository.BookRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Business logic for managing personal reading log entries. */
@Service
@RequiredArgsConstructor
public class BookService {

  private final BookRepository bookRepository;
  private final ObjectMapper objectMapper;

  private static final Path DEFAULT_EXPORT_PATH = Path.of("books.json");

  public BookResponse create(BookRequest request) {
    Book book =
        Book.builder()
            .bookName(request.bookName())
            .author(request.author())
            .review(request.review())
            .build();
    return BookResponse.from(bookRepository.save(book));
  }

  public List<BookResponse> findAll() {
    return bookRepository.findAll().stream().map(BookResponse::from).toList();
  }

  public BookResponse findById(Long id) {
    return BookResponse.from(getBookOrThrow(id));
  }

  public BookResponse update(Long id, BookRequest request) {
    Book book = getBookOrThrow(id);
    book.setBookName(request.bookName());
    book.setAuthor(request.author());
    book.setReview(request.review());
    return BookResponse.from(bookRepository.save(book));
  }

  public void delete(Long id) {
    Book book = getBookOrThrow(id);
    bookRepository.delete(book);
  }

  public Path exportToJson() {
    return exportToJson(DEFAULT_EXPORT_PATH);
  }

  public Path exportToJson(Path target) {
    try {
      List<Book> books = bookRepository.findAll();
      objectMapper.writeValue(target.toFile(), books);
      return target;
    } catch (IOException e) {
      throw new BookExportException("Failed to export books to JSON file", e);
    }
  }

  private Book getBookOrThrow(Long id) {
    return bookRepository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
  }
}
