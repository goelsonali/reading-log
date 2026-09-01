package com.readinglog.app.agent.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.readinglog.app.agent.manifest.ManifestLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the same capability manifest an MCP client sees via tool listing, for non-MCP
 * callers (e.g. building a function-calling tool schema). Reads from the same
 * {@link ManifestLoader} the MCP tool registration uses — never a second, independently
 * maintained copy (agentic-action-layer constitution.md, Article 1).
 */
@RestController
@RequestMapping("/api/v1/agent/books")
public class AgentManifestController {

  private final ManifestLoader manifestLoader;

  public AgentManifestController(ManifestLoader manifestLoader) {
    this.manifestLoader = manifestLoader;
  }

  @GetMapping("/manifest")
  public ResponseEntity<JsonNode> manifest() {
    return ResponseEntity.ok(manifestLoader.raw());
  }
}
