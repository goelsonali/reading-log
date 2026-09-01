package com.readinglog.app.agent.envelope;

/**
 * Present when a failure is something the caller can correct and retry itself, per
 * error-envelope.schema.json's {@code fixHint}.
 *
 * @param field the input field that needs to change
 * @param problem what's wrong with it, stated as a constraint
 * @param suggestedValue a corrected value the caller can retry with, if one can be computed
 */
public record FixHint(String field, String problem, Object suggestedValue) {}
