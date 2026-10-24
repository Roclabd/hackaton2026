package blueprints.graph;

/**
 * Ребро схемы — форма из docs/API.md.
 *
 * @param label для CALL: «вызов» (точно) или «вызов?» (сопоставлено по имени, см. {@link CallResolver})
 */
public record GraphEdge(String id, String from, String to, EdgeKind kind, String label) {}
