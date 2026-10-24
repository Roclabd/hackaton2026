import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Раздел 3 документа: коллекции java.util. Порядок обхода HashSet/HashMap для Integer и String в одной JDK
 * детерминирован; для Set.of/Map.of порядок случайный, поэтому их содержимое печатается через TreeSet/TreeMap.
 * Ввод — целые числа через пробел; если их нет, берутся числа по умолчанию.
 */
public class Scope3Collections {

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        List<Integer> numbers = new ArrayList<>();
        while (in.hasNext() && numbers.size() < 30) {
            if (in.hasNextInt()) {
                numbers.add(in.nextInt());
            } else {
                in.next();
            }
        }
        if (numbers.isEmpty()) {
            numbers.addAll(Arrays.asList(5, 3, 8, 3, 1, 9, 5));
        }
        collectionBasics(numbers);
        lists(numbers);
        sets(numbers);
        maps(numbers);
        iterators(numbers);
        helperClasses(numbers);
        immutableFactories();
    }

    static void collectionBasics(List<Integer> numbers) {
        // 3.Collection: интерфейс Collection (add, size, isEmpty, contains, remove, clear)
        Collection<Integer> collection = new ArrayList<>();
        System.out.println(collection.isEmpty());
        for (Integer value : numbers) {
            collection.add(value);
        }
        System.out.println(collection.size() + " " + collection.contains(3) + " " + collection.contains(100));
        // 3.Collection: remove(Object) удаляет первое вхождение
        collection.remove(Integer.valueOf(3));
        System.out.println(collection);
        // 3.Collection: addAll, containsAll, toArray
        Collection<Integer> other = new HashSet<>(Arrays.asList(1, 2));
        collection.addAll(other);
        System.out.println(collection.containsAll(other) + " " + collection.toArray().length);
        // 3.Iterable: for-each по коллекции
        int total = 0;
        for (int v : collection) {
            total += v;
        }
        System.out.println("total=" + total);
        // 3.Collection: clear
        collection.clear();
        System.out.println(collection.size() + " " + collection.isEmpty());
    }

    static void lists(List<Integer> numbers) {
        // 3.List: ArrayList (add, add по индексу, get, set, indexOf, remove по индексу)
        List<Integer> array = new ArrayList<>(numbers);
        array.add(42);
        array.add(0, -1);
        array.set(1, array.get(1) + 100);
        System.out.println(array + " " + array.indexOf(42) + " " + array.lastIndexOf(42));
        // 3.List: remove(int) и remove(Object) — разные методы
        array.remove(0);
        array.remove(Integer.valueOf(42));
        System.out.println(array);
        // 3.List: subList и сортировка
        List<Integer> part = array.subList(0, Math.min(3, array.size()));
        List<Integer> sorted = new ArrayList<>(part);
        Collections.sort(sorted);
        System.out.println(part + " " + sorted);
        // 3.List: LinkedList (addFirst, addLast, removeFirst, getFirst, peekLast)
        LinkedList<String> linked = new LinkedList<>();
        linked.add("b");
        linked.addFirst("a");
        linked.addLast("c");
        System.out.println(linked + " " + linked.getFirst() + " " + linked.peekLast() + " " + linked.removeFirst()
                + " " + linked);
        // 3.List: LinkedList как очередь (Queue: offer, peek, poll)
        LinkedList<Integer> queue = new LinkedList<>();
        queue.offer(1);
        queue.offer(2);
        System.out.println(queue.peek() + " " + queue.poll() + " " + queue.poll() + " " + queue.poll());
        // 3.List: ArrayDeque как стек и двусторонняя очередь
        Deque<Integer> deque = new ArrayDeque<>();
        deque.push(1);
        deque.push(2);
        deque.addLast(3);
        System.out.println(deque + " " + deque.pop() + " " + deque.pollLast());
        // 3.List: по индексу в цикле и сравнение списков
        int evens = 0;
        for (int i = 0; i < array.size(); i++) {
            if (array.get(i) % 2 == 0) {
                evens++;
            }
        }
        System.out.println(evens + " " + array.equals(new ArrayList<>(array)) + " " + array.contains(5));
    }

    static void sets(List<Integer> numbers) {
        // 3.Set: HashSet убирает повторы
        Set<Integer> hash = new HashSet<>(numbers);
        System.out.println(hash.size() + " " + hash.contains(5) + " " + hash.add(5) + " " + hash.add(1000));
        // 3.Set: LinkedHashSet хранит порядок вставки
        Set<Integer> linked = new LinkedHashSet<>(numbers);
        System.out.println(linked);
        // 3.Set: TreeSet — сортированное множество
        TreeSet<Integer> tree = new TreeSet<>(numbers);
        System.out.println(tree + " " + tree.first() + " " + tree.last());
        // 3.Set: SortedSet (headSet, tailSet, subSet)
        SortedSet<Integer> sortedSet = tree;
        System.out.println(sortedSet.headSet(5) + " " + sortedSet.tailSet(5) + " " + sortedSet.subSet(3, 9));
        // 3.Set: NavigableSet (floor, ceiling, higher, lower, descendingSet, pollFirst)
        NavigableSet<Integer> navigable = new TreeSet<>(tree);
        System.out.println(navigable.floor(4) + " " + navigable.ceiling(4) + " " + navigable.higher(5) + " "
                + navigable.lower(5) + " " + navigable.descendingSet() + " " + navigable.pollFirst() + " " + navigable);
        // 3.Set: операции над множествами (union, intersection, difference)
        Set<Integer> a = new TreeSet<>(Arrays.asList(1, 2, 3, 4));
        Set<Integer> b = new TreeSet<>(Arrays.asList(3, 4, 5));
        Set<Integer> union = new TreeSet<>(a);
        union.addAll(b);
        Set<Integer> common = new TreeSet<>(a);
        common.retainAll(b);
        Set<Integer> diff = new TreeSet<>(a);
        diff.removeAll(b);
        System.out.println(union + " " + common + " " + diff);
        // 3.Set: TreeSet со своим Comparator
        TreeSet<String> byLength = new TreeSet<>(new Comparator<String>() {
            @Override
            public int compare(String x, String y) {
                int byLen = Integer.compare(x.length(), y.length());
                return byLen != 0 ? byLen : x.compareTo(y);
            }
        });
        byLength.addAll(Arrays.asList("pear", "fig", "apple", "kiwi", "fig"));
        System.out.println(byLength);
    }

    static void maps(List<Integer> numbers) {
        // 3.Map: HashMap — подсчёт частот (put, get, getOrDefault, containsKey)
        Map<Integer, Integer> freq = new HashMap<>();
        for (Integer value : numbers) {
            freq.put(value, freq.getOrDefault(value, 0) + 1);
        }
        System.out.println(freq + " " + freq.get(5) + " " + freq.get(12345) + " " + freq.containsKey(3)
                + " " + freq.containsValue(2) + " " + freq.size());
        // 3.Map: putIfAbsent, merge, remove
        freq.putIfAbsent(77, 0);
        freq.putIfAbsent(77, 5);
        freq.merge(77, 10, Integer::sum);
        freq.remove(3);
        System.out.println(freq);
        // 3.Map: LinkedHashMap хранит порядок вставки
        Map<String, Integer> linked = new LinkedHashMap<>();
        linked.put("z", 1);
        linked.put("a", 2);
        linked.put("m", 3);
        System.out.println(linked + " " + linked.keySet() + " " + linked.values());
        // 3.Map: Map.Entry — обход entrySet
        StringBuilder entries = new StringBuilder();
        for (Map.Entry<String, Integer> entry : linked.entrySet()) {
            entries.append(entry.getKey()).append('=').append(entry.getValue() * 10).append(';');
            entry.setValue(entry.getValue() + 1);
        }
        System.out.println(entries + " " + linked);
        // 3.Map: TreeMap — сортированная карта
        TreeMap<String, Integer> tree = new TreeMap<>(linked);
        tree.put("b", 9);
        System.out.println(tree + " " + tree.firstKey() + " " + tree.lastKey());
        // 3.Map: SortedMap (headMap, tailMap, subMap)
        SortedMap<String, Integer> sortedMap = tree;
        System.out.println(sortedMap.headMap("m") + " " + sortedMap.tailMap("m") + " " + sortedMap.subMap("a", "c"));
        // 3.Map: NavigableMap (floorKey, ceilingKey, firstEntry, pollFirstEntry, descendingMap)
        NavigableMap<String, Integer> navigable = new TreeMap<>(tree);
        System.out.println(navigable.floorKey("c") + " " + navigable.ceilingKey("c") + " " + navigable.firstEntry()
                + " " + navigable.lastEntry().getValue() + " " + navigable.descendingMap() + " "
                + navigable.pollFirstEntry() + " " + navigable);
        // 3.Map: значения-списки (группировка вручную)
        Map<Boolean, List<Integer>> parity = new TreeMap<>();
        for (Integer value : numbers) {
            boolean even = value % 2 == 0;
            if (!parity.containsKey(even)) {
                parity.put(even, new ArrayList<>());
            }
            parity.get(even).add(value);
        }
        System.out.println(parity);
    }

    static void iterators(List<Integer> numbers) {
        // 3.Iterator: hasNext, next, remove
        List<Integer> list = new ArrayList<>(numbers);
        Iterator<Integer> it = list.iterator();
        while (it.hasNext()) {
            if (it.next() % 2 == 1) {
                it.remove();
            }
        }
        System.out.println(list);
        // 3.ListIterator: hasPrevious, previous, set, add, nextIndex
        List<String> names = new ArrayList<>(Arrays.asList("a", "b", "c"));
        ListIterator<String> li = names.listIterator();
        while (li.hasNext()) {
            int index = li.nextIndex();
            String name = li.next();
            if (name.equals("b")) {
                li.set("B");
                li.add("b2");
            }
            System.out.print(index + name + " ");
        }
        StringBuilder back = new StringBuilder();
        while (li.hasPrevious()) {
            back.append(li.previous());
        }
        System.out.println(back + " " + names);
        // 3.Iterator: явный обход Set итератором
        Set<String> set = new TreeSet<>(Arrays.asList("x", "y", "z"));
        StringBuilder joined = new StringBuilder();
        for (Iterator<String> si = set.iterator(); si.hasNext(); ) {
            joined.append(si.next());
            if (si.hasNext()) {
                joined.append('+');
            }
        }
        System.out.println(joined);
    }

    static void helperClasses(List<Integer> numbers) {
        // 3.Collections: sort, reverse, max, min, frequency, swap, binarySearch
        List<Integer> list = new ArrayList<>(numbers);
        Collections.sort(list);
        int found = Collections.binarySearch(list, list.get(list.size() / 2));
        System.out.println(list + " " + (found >= 0) + " " + Collections.max(list) + " " + Collections.min(list)
                + " " + Collections.frequency(list, list.get(0)));
        Collections.reverse(list);
        if (list.size() > 1) {
            Collections.swap(list, 0, list.size() - 1);
        }
        System.out.println(list);
        // 3.Collections: shuffle с фиксированным зерном (детерминирован)
        Collections.shuffle(list, new Random(1));
        System.out.println(list.size());
        // 3.Collections: unmodifiableList, emptyList, nCopies
        List<Integer> view = Collections.unmodifiableList(list);
        System.out.println(view.size() + " " + Collections.emptyList() + " " + Collections.nCopies(3, "ab"));
        // 3.Arrays: asList, sort, toString, fill, copyOf, copyOfRange, equals, binarySearch
        int[] array = {9, 4, 7, 1};
        Arrays.sort(array);
        int[] copy = Arrays.copyOf(array, 6);
        int[] slice = Arrays.copyOfRange(array, 1, 3);
        int[] filled = new int[3];
        Arrays.fill(filled, 8);
        System.out.println(Arrays.toString(array) + Arrays.toString(copy) + Arrays.toString(slice)
                + Arrays.toString(filled) + Arrays.equals(array, array.clone()) + Arrays.binarySearch(array, 7));
        List<String> fixed = Arrays.asList("c", "a", "b");
        Collections.sort(fixed);
        System.out.println(fixed);
        // 3.Comparator: анонимный класс, naturalOrder, reverseOrder, reversed
        List<String> words = new ArrayList<>(Arrays.asList("pear", "fig", "banana", "kiwi"));
        words.sort(new Comparator<String>() {
            @Override
            public int compare(String x, String y) {
                return Integer.compare(x.length(), y.length());
            }
        });
        System.out.println(words);
        words.sort(Comparator.naturalOrder());
        System.out.println(words);
        words.sort(Comparator.reverseOrder());
        System.out.println(words);
        Comparator<String> byLength = (x, y) -> x.length() - y.length();
        words.sort(byLength.reversed());
        System.out.println(words);
        // 3.Objects: equals, hash, requireNonNull, toString с запасным значением, isNull, requireNonNullElse
        String maybe = null;
        System.out.println(Objects.equals(maybe, null) + " " + Objects.equals("a", "b") + " "
                + Objects.hash(1, 2, 3) + " " + Objects.toString(maybe, "пусто") + " " + Objects.isNull(maybe)
                + " " + Objects.nonNull(maybe) + " " + Objects.requireNonNullElse(maybe, "запас")
                + " " + Objects.hashCode(maybe));
        // 3.Optional: of, empty, ofNullable, isPresent, get, orElse, isEmpty
        Optional<Integer> first = list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        Optional<String> none = Optional.ofNullable(maybe);
        System.out.println(first.isPresent() + " " + first.get().getClass().getSimpleName() + " " + none.isPresent()
                + " " + none.isEmpty() + " " + none.orElse("нет") + " " + Optional.of("x").orElse("y"));
    }

    static void immutableFactories() {
        // 3.Фабрики: List.of
        List<Integer> immutableList = List.of(3, 1, 2);
        System.out.println(immutableList + " " + immutableList.size() + " " + immutableList.contains(2));
        // 3.Фабрики: Set.of (порядок не определён — печатаем через TreeSet)
        Set<String> immutableSet = Set.of("b", "a", "c");
        System.out.println(new TreeSet<>(immutableSet) + " " + immutableSet.contains("a"));
        // 3.Фабрики: Map.of (порядок не определён — печатаем через TreeMap)
        Map<String, Integer> immutableMap = Map.of("one", 1, "two", 2, "three", 3);
        System.out.println(new TreeMap<>(immutableMap) + " " + immutableMap.get("two"));
        // 3.Фабрики: изменение неизменяемой коллекции — UnsupportedOperationException
        try {
            immutableList.add(4);
            System.out.println("добавили");
        } catch (UnsupportedOperationException e) {
            System.out.println("неизменяемая");
        }
        // 3.Фабрики: копии List.copyOf, Map.entry
        List<Integer> copy = List.copyOf(new ArrayList<>(immutableList));
        Map.Entry<String, Integer> entry = Map.entry("k", 5);
        System.out.println(copy + " " + entry.getKey() + entry.getValue());
    }
}
