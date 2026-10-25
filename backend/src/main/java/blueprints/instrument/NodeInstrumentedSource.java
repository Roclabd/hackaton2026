package blueprints.instrument;

import java.util.List;

/** Копия со счётчиками; позиция id в списке совпадает с индексом счётчика рабочего процесса. */
public record NodeInstrumentedSource(String source, List<String> nodeIds) {
    public NodeInstrumentedSource {
        nodeIds = List.copyOf(nodeIds);
    }
}
