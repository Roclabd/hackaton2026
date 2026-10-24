package blueprints.graph;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Типы рёбер схемы.
 * CALL — «метод вызывает метод» (уровень 0); EXEC — порядок выполнения операторов (уровень 1).
 */
public enum EdgeKind {
    CALL, EXEC;

    @JsonValue
    public String wire() {
        return name().toLowerCase();
    }
}
