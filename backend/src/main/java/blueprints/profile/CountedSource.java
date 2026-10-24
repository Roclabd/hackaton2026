package blueprints.profile;

import java.util.List;

/**
 * Копия кода со счётчиком в теле каждого цикла. По ней только считают — вердикт о поведении не выносится.
 *
 * @param source текст копии
 * @param loops  все циклы файла в порядке появления; номер счётчика — {@link LoopSite#counter()}
 */
record CountedSource(String source, List<LoopSite> loops) {

    CountedSource {
        loops = List.copyOf(loops);
    }

    /** Номера счётчиков циклов, которые начинаются в строках [from, to]. */
    List<Integer> countersWithin(int from, int to) {
        return within(from, to).stream().map(LoopSite::counter).toList();
    }

    /** Глубина вложенности циклов в строках [from, to]: два цикла один в другом — 2; циклов нет — 0. */
    int nestingWithin(int from, int to) {
        List<LoopSite> region = within(from, to);
        if (region.isEmpty()) {
            return 0;
        }
        int outer = region.stream().mapToInt(LoopSite::depth).min().orElse(1);
        int inner = region.stream().mapToInt(LoopSite::depth).max().orElse(1);
        return inner - outer + 1;
    }

    private List<LoopSite> within(int from, int to) {
        return loops.stream().filter(loop -> loop.startsWithin(from, to)).toList();
    }
}
