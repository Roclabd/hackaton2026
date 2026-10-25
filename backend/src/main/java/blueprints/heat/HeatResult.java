package blueprints.heat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Сумма реальных посещений измеримых операторов выбранного метода по всем прогонам. */
public record HeatResult(String methodId, int runs, Map<String, Long> counts, long maxCount, String note) {
    public HeatResult {
        counts = Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }
}
