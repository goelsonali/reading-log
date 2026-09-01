package com.readinglog.app.agent.mcp;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Mounts the 6 book actions as MCP tools over Spring WebMVC's SSE transport, per
 * openspec/changes/add-agentic-action-layer-books/design.md, Decision 1. Agents connect to
 * {@code /mcp/sse} (the transport's default SSE endpoint) and post messages to
 * {@code /mcp/message}.
 */
@Configuration
public class BookMcpServerConfig {

  @Bean
  public WebMvcSseServerTransportProvider bookMcpTransportProvider() {
    return WebMvcSseServerTransportProvider.builder().messageEndpoint("/mcp/message").build();
  }

  @Bean
  public RouterFunction<ServerResponse> bookMcpRouterFunction(WebMvcSseServerTransportProvider transportProvider) {
    return transportProvider.getRouterFunction();
  }

  @Bean
  public McpSyncServer bookMcpServer(
      WebMvcSseServerTransportProvider transportProvider, BookMcpToolHandlers toolHandlers) {
    return McpServer.sync(transportProvider)
        .serverInfo("reading-log-books", "0.0.1-SNAPSHOT")
        .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
        .tools(toolHandlers.toolSpecifications())
        .build();
  }
}
