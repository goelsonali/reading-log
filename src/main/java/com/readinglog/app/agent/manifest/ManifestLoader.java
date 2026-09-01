package com.readinglog.app.agent.manifest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads the single capability manifest resource ({@code agent/books-manifest.json}) once at
 * startup. This is the ONE discovery surface for the books agent actions (Constitution
 * Article 1 in .claude/skills/agentic-action-layer): the manifest REST endpoint and the MCP
 * tool registrations both read from this same parsed manifest, never a second, independently
 * maintained copy.
 */
@Component
public class ManifestLoader {

  private static final String MANIFEST_RESOURCE = "agent/books-manifest.json";

  private final ObjectMapper objectMapper;
  private final JsonNode manifest;

  public ManifestLoader(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.manifest = readManifest(objectMapper);
  }

  private static JsonNode readManifest(ObjectMapper objectMapper) {
    try (InputStream in = new ClassPathResource(MANIFEST_RESOURCE).getInputStream()) {
      return objectMapper.readTree(in);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load capability manifest: " + MANIFEST_RESOURCE, e);
    }
  }

  /** The raw manifest document, exactly as authored — served verbatim by the REST discovery endpoint. */
  public JsonNode raw() {
    return manifest;
  }

  /** All action entries, in manifest order. */
  public List<JsonNode> actions() {
    List<JsonNode> result = new ArrayList<>();
    manifest.get("actions").forEach(result::add);
    return result;
  }

  /** A single action entry by name, or throws if the manifest has no such action. */
  public JsonNode action(String name) {
    for (JsonNode action : actions()) {
      if (action.get("name").asText().equals(name)) {
        return action;
      }
    }
    throw new NoSuchElementException("No manifest entry for action: " + name);
  }

  /** The action's intent text, verbatim — the same text used for MCP tool discovery. */
  public String intent(String name) {
    return action(name).get("intent").asText();
  }

  /** The action's input JSON Schema, re-serialized to a JSON string for MCP Tool registration. */
  public String inputSchemaJson(String name) {
    try {
      return objectMapper.writeValueAsString(action(name).get("inputSchema"));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to serialize input schema for action: " + name, e);
    }
  }

  /** The manifest as a plain Map tree, for callers that want it outside the Jackson tree model. */
  public Map<String, Object> asMap() {
    return objectMapper.convertValue(manifest, Map.class);
  }
}
