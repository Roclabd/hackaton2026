package blueprints.graph;

/** Ветка контейнера на чертеже метода: ключ (then, else, body, case:N, catch:N, finally) и подпись для экрана. */
public record GraphBranch(String key, String label) {}
