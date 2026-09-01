package com.readinglog.app.agent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readinglog.app.agent.action.BookAgentActions;
import com.readinglog.app.agent.envelope.ActionException;
import com.readinglog.app.agent.envelope.ErrorEnvelope;
import com.readinglog.app.agent.manifest.ManifestLoader;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds the MCP {@code SyncToolSpecification} for each of the 6 book actions, straight from
 * the capability manifest — the tool name, description, and input schema all come from
 * {@link ManifestLoader} verbatim (agentic-action-layer constitution.md, Article 1: no
 * second, independently maintained description). Each handler delegates to
 * {@link BookAgentActions} and turns a thrown {@link ActionException} into an
 * {@link ErrorEnvelope}-shaped {@code structuredContent} with {@code isError: true}, rather
 * than letting a raw exception reach the agent.
 */
@Component
public class BookMcpToolHandlers {

  private static final List<String> ACTION_NAMES =
      List.of("list_books", "get_book", "create_book", "update_book", "delete_book", "export_books");

  private final ManifestLoader manifestLoader;
  private final BookAgentActions actions;
  private final ObjectMapper objectMapper;

  public BookMcpToolHandlers(ManifestLoader manifestLoader, BookAgentActions actions, ObjectMapper objectMapper) {
    this.manifestLoader = manifestLoader;
    this.actions = actions;
    this.objectMapper = objectMapper;
  }

  public List<McpServerFeatures.SyncToolSpecification> toolSpecifications() {
    List<McpServerFeatures.SyncToolSpecification> specs = new ArrayList<>();
    for (String actionName : ACTION_NAMES) {
      specs.add(
          McpServerFeatures.SyncToolSpecification.builder()
              .tool(buildTool(actionName))
              .callHandler((exchange, request) -> invoke(actionName, request))
              .build());
    }
    return specs;
  }

  private McpSchema.Tool buildTool(String actionName) {
    return McpSchema.Tool.builder()
        .name(actionName)
        .description(manifestLoader.intent(actionName))
        .inputSchema(McpJsonDefaults.getMapper(), manifestLoader.inputSchemaJson(actionName))
        .build();
  }

  private McpSchema.CallToolResult invoke(String actionName, McpSchema.CallToolRequest request) {
    Map<String, Object> args = request.arguments() == null ? Map.of() : request.arguments();
    try {
      Map<String, Object> result =
          switch (actionName) {
            case "list_books" -> actions.listBooks();
            case "get_book" -> actions.getBook(longArg(args, "id"));
            case "create_book" -> actions.createBook(
                stringArg(args, "bookName"), stringArg(args, "author"), stringArg(args, "review"), stringArg(args, "idempotencyKey"));
            case "update_book" -> actions.updateBook(
                longArg(args, "id"),
                stringArg(args, "bookName"),
                stringArg(args, "author"),
                stringArg(args, "review"),
                stringArg(args, "idempotencyKey"));
            case "delete_book" -> actions.deleteBook(
                longArg(args, "id"),
                stringArg(args, "idempotencyKey"),
                booleanArg(args, "dryRun"),
                stringArg(args, "confirmationToken"));
            case "export_books" -> actions.exportBooks(stringArg(args, "idempotencyKey"));
            default -> throw new IllegalStateException("Unknown action: " + actionName);
          };
      return McpSchema.CallToolResult.builder().structuredContent(result).isError(false).build();
    } catch (ActionException e) {
      Map<String, Object> errorMap = objectMapper.convertValue(ErrorEnvelope.of(e.error()), Map.class);
      return McpSchema.CallToolResult.builder()
          .structuredContent(errorMap)
          .isError(true)
          .addContent(new McpSchema.TextContent(e.error().message()))
          .build();
    }
  }

  private static Long longArg(Map<String, Object> args, String key) {
    Object value = args.get(key);
    return value instanceof Number number ? number.longValue() : null;
  }

  private static String stringArg(Map<String, Object> args, String key) {
    Object value = args.get(key);
    return value == null ? null : value.toString();
  }

  private static boolean booleanArg(Map<String, Object> args, String key) {
    Object value = args.get(key);
    return value instanceof Boolean bool && bool;
  }
}
