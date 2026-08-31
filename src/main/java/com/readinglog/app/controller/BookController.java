package com.readinglog.app.controller;

import com.readinglog.app.dto.BookRequest;
import com.readinglog.app.dto.BookResponse;
import com.readinglog.app.service.BookService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for managing personal reading log entries. */
@RestController
@RequestMapping("/api/v1/books")
@RequiredArgsConstructor
public class BookController {

  private final BookService bookService;

  @PostMapping
  public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(bookService.create(request));
  }

  @PostMapping("/export")
  public ResponseEntity<Void> exportToJson() {
    bookService.exportToJson();
    return ResponseEntity.ok().build();
  }

  @GetMapping
  public ResponseEntity<List<BookResponse>> findAll() {
    return ResponseEntity.ok(bookService.findAll());
  }

  @GetMapping("/{id}")
  public ResponseEntity<BookResponse> findById(@PathVariable Long id) {
    return ResponseEntity.ok(bookService.findById(id));
  }

  @PutMapping("/{id}")
  public ResponseEntity<BookResponse> update(
      @PathVariable Long id, @Valid @RequestBody BookRequest request) {
    return ResponseEntity.ok(bookService.update(id, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    bookService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
