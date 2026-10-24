package blueprints.graph;

/**
 * Оценка сложности метода или цикла по структуре кода — бриф требует «оценку сложности метода или фрагмента».
 * Это оценка, а не замер: n — размер входных данных (коллекций, массивов, ввода).
 *
 * @param big   запись в нотации O: «O(1)», «O(log n)», «O(n)», «O(n log n)», «O(n²)»… или «рекурсия»
 * @param level для цвета на схеме: {@code low} — O(1) и O(log n), {@code linear} — O(n) и O(n log n),
 *              {@code high} — O(n²) и выше, {@code unknown} — рекурсия (по структуре не оценить)
 * @param why   откуда оценка, по-русски: какие циклы и вызовы её дали, со строками
 */
public record Complexity(String big, String level, String why) {}
