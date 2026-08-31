package com.readinglog.app.dto;

import com.readinglog.app.domain.Book;

/**
 * Response payload representing a reading log entry.
 *
 * @param id unique identifier of the entry
 * @param bookName title of the book
 * @param author author of the book
 * @param review the reader's review of the book
 */
public record BookResponse(Long id, String bookName, String author, String review) {

  public static BookResponse from(Book book) {
    return new BookResponse(book.getId(), book.getBookName(), book.getAuthor(), book.getReview());
  }
}
