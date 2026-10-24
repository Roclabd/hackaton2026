package blueprints.gate;

/** Сводка по группе входов: сколько проверено и сколько совпало. */
public record GroupSummary(String name, int total, int checked, int matched, VerdictStatus status) {}
