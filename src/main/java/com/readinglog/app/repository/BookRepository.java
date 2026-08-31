package com.readinglog.app.repository;

import com.readinglog.app.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link Book} entities. */
public interface BookRepository extends JpaRepository<Book, Long> {}
