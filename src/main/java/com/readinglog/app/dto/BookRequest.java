package com.readinglog.app.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for creating or updating a reading log entry.
 *
 * @param bookName title of the book
 * @param author author of the book
 * @param review the reader's review of the book
 */
public record BookRequest(
    @NotBlank(message = "bookName is required") String bookName,
    @NotBlank(message = "author is required") String author,
    String review) {}
