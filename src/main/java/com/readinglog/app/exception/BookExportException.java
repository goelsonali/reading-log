package com.readinglog.app.exception;

/** Thrown when the book export to a JSON file fails. */
public class BookExportException extends RuntimeException {

  public BookExportException(String message, Throwable cause) {
    super(message, cause);
  }
}
