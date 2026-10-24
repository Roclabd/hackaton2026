package blueprints.profile;

/**
 * Цикл под счётчиком.
 *
 * @param counter номер счётчика в массиве {@code __BlueprintsCoverage.HITS}
 * @param line    строка (с 1), где начинается цикл, — в исходнике и в копии она одна и та же
 * @param endLine строка, где цикл заканчивается
 * @param depth   вложенность: 1 — цикл не внутри другого цикла, 2 — внутри одного и т. д.
 */
record LoopSite(int counter, int line, int endLine, int depth) {

    /** Начинается ли цикл в строках [from, to] включительно. */
    boolean startsWithin(int from, int to) {
        return from <= line && line <= to;
    }
}
