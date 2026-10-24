package blueprints.diag;

import java.util.List;

/** Находка AST-правила; рост отсутствует у правил, которые не оценивают сложность. */
public record Finding(String id, String rule, String methodId, int[] lines, String title, String why,
                      String growthBefore, String growthAfter, List<String> conditions, List<Patch> patches,
                      String category) {
    public static final String DEFECT = "DEFECT";
    public static final String INEFFICIENCY = "INEFFICIENCY";
    public static final String DEAD_CODE = "DEAD_CODE";

    /** Совместимость с существующим R1 и клиентами Java. */
    public Finding(String id, String rule, String methodId, int[] lines, String title, String why,
                   String growthBefore, String growthAfter, List<String> conditions, List<Patch> patches) {
        this(id, rule, methodId, lines, title, why, growthBefore, growthAfter, conditions, patches, INEFFICIENCY);
    }
}
