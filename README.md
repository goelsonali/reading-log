# Personal Reading Log

A simple Spring Boot REST API to track books you've read: book name, author, and your review.

## Tech Stack

- Java 21
- Spring Boot 3.2.5 (Web, Data JPA, Validation)
- H2 in-memory database
- Gradle

## Run

```bash
./gradlew bootRun
```

The app starts on `http://localhost:8081`.

- Swagger UI: http://localhost:8081/swagger-ui/index.html
- H2 console: http://localhost:8081/h2-console (JDBC URL: `jdbc:h2:mem:readinglog`, user: `sa`, no password)

## API

| Method | Path              | Description          |
|--------|-------------------|-----------------------|
| POST   | `/api/v1/books`      | Create a book entry  |
| GET    | `/api/v1/books`      | List all book entries|
| GET    | `/api/v1/books/{id}` | Get a book entry     |
| PUT    | `/api/v1/books/{id}` | Update a book entry  |
| DELETE | `/api/v1/books/{id}` | Delete a book entry  |
| POST   | `/api/v1/books/export` | Save all book entries to a JSON file |

### Example

```bash
curl -X POST http://localhost:8081/api/v1/books \
  -H "Content-Type: application/json" \
  -d '{"bookName": "Clean Code", "author": "Robert C. Martin", "review": "Great read"}'
```

## Test

```bash
./gradlew test
```
