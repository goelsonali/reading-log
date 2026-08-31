package com.readinglog.app.exception;

/** Thrown when a requested reading log entry cannot be found. */
public class BookNotFoundException extends RuntimeException {

  public BookNotFoundException(Long id) {
    super("Book not found with id: " + id);
  }
}
