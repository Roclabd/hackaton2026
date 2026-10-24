package blueprints.graph;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Типы узлов схемы. В JSON пишутся строчными буквами, как в docs/API.md.
 *
 * Уровень 0 («Карта»): CLASS, FIELD, METHOD.
 * Уровень 1 («Чертёж метода»): остальные.
 */
public enum NodeKind {
    CLASS, FIELD, METHOD,
    STMT, IF, LOOP, SWITCH, TRY, RETURN, THROW, BREAK, CONTINUE, RAW;

    @JsonValue
    public String wire() {
        return name().toLowerCase();
    }
}
