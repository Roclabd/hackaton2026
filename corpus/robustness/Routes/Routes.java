import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;

/**
 * Граф маршрутов между городами: поиск в ширину (меньше пересадок) и Дейкстра (короче путь).
 * Ввод: строки «откуда куда», например «Ростов Сочи». Пустая строка или конец ввода — выход.
 */
public class Routes {

    record Edge(String to, int km) {}

    static class Graph {
        private final Map<String, List<Edge>> adj = new LinkedHashMap<>();

        void road(String a, String b, int km) {
            adj.computeIfAbsent(a, k -> new ArrayList<>()).add(new Edge(b, km));
            adj.computeIfAbsent(b, k -> new ArrayList<>()).add(new Edge(a, km));
        }

        boolean has(String city) {
            return adj.containsKey(city);
        }

        Set<String> cities() {
            return new TreeSet<>(adj.keySet());
        }

        /** Путь с наименьшим числом пересадок. */
        List<String> bfs(String from, String to) {
            Map<String, String> parent = new HashMap<>();
            Deque<String> queue = new ArrayDeque<>();
            Set<String> visited = new HashSet<>();
            queue.add(from);
            visited.add(from);
            while (!queue.isEmpty()) {
                String cur = queue.poll();
                if (cur.equals(to)) {
                    return restore(parent, from, to);
                }
                for (Edge e : adj.getOrDefault(cur, List.of())) {
                    if (visited.add(e.to())) {
                        parent.put(e.to(), cur);
                        queue.add(e.to());
                    }
                }
            }
            return List.of();
        }

        /** Кратчайший по километрам путь. */
        Result dijkstra(String from, String to) {
            Map<String, Integer> dist = new HashMap<>();
            Map<String, String> parent = new HashMap<>();
            PriorityQueue<int[]> pq = new PriorityQueue<>((x, y) -> x[0] != y[0] ? Integer.compare(x[0], y[0])
                    : Integer.compare(x[1], y[1]));
            List<String> index = new ArrayList<>(adj.keySet());
            dist.put(from, 0);
            pq.add(new int[]{0, index.indexOf(from)});
            while (!pq.isEmpty()) {
                int[] top = pq.poll();
                String cur = index.get(top[1]);
                if (top[0] > dist.getOrDefault(cur, Integer.MAX_VALUE)) {
                    continue;
                }
                if (cur.equals(to)) {
                    break;
                }
                for (Edge e : adj.get(cur)) {
                    int nd = top[0] + e.km();
                    if (nd < dist.getOrDefault(e.to(), Integer.MAX_VALUE)) {
                        dist.put(e.to(), nd);
                        parent.put(e.to(), cur);
                        pq.add(new int[]{nd, index.indexOf(e.to())});
                    }
                }
            }
            if (!dist.containsKey(to)) {
                return new Result(List.of(), -1);
            }
            return new Result(restore(parent, from, to), dist.get(to));
        }

        private static List<String> restore(Map<String, String> parent, String from, String to) {
            List<String> path = new ArrayList<>();
            String cur = to;
            while (cur != null) {
                path.add(cur);
                if (cur.equals(from)) {
                    break;
                }
                cur = parent.get(cur);
            }
            Collections.reverse(path);
            return path;
        }

        /** Компоненты связности — каким городам можно доехать друг до друга. */
        List<Set<String>> components() {
            List<Set<String>> result = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String start : cities()) {
                if (seen.contains(start)) {
                    continue;
                }
                Set<String> comp = new TreeSet<>();
                dfs(start, seen, comp);
                result.add(comp);
            }
            return result;
        }

        private void dfs(String city, Set<String> seen, Set<String> comp) {
            if (!seen.add(city)) {
                return;
            }
            comp.add(city);
            for (Edge e : adj.get(city)) {
                dfs(e.to(), seen, comp);
            }
        }
    }

    record Result(List<String> path, int km) {}

    static Graph southRussia() {
        Graph g = new Graph();
        g.road("Ростов", "Краснодар", 280);
        g.road("Ростов", "Таганрог", 70);
        g.road("Ростов", "Волгоград", 470);
        g.road("Краснодар", "Сочи", 290);
        g.road("Краснодар", "Новороссийск", 150);
        g.road("Новороссийск", "Сочи", 260);
        g.road("Ростов", "Ставрополь", 360);
        g.road("Ставрополь", "Сочи", 600);
        g.road("Волгоград", "Астрахань", 430);
        g.road("Ялта", "Севастополь", 80);
        return g;
    }

    public static void main(String[] args) {
        Graph g = southRussia();
        System.out.println("Города: " + g.cities());
        System.out.println("Связные группы: " + g.components().size());
        Scanner sc = new Scanner(System.in);
        int queries = 0;
        int found = 0;
        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) {
                break;
            }
            queries++;
            String[] p = line.split("\\s+");
            if (p.length != 2) {
                System.out.println("Нужно два города, а не " + p.length + ": " + line);
                continue;
            }
            if (!g.has(p[0]) || !g.has(p[1])) {
                System.out.println("Неизвестный город в запросе: " + line);
                continue;
            }
            List<String> hops = g.bfs(p[0], p[1]);
            Result best = g.dijkstra(p[0], p[1]);
            if (best.km() < 0) {
                System.out.println(p[0] + " → " + p[1] + ": дороги нет");
                continue;
            }
            found++;
            System.out.println(p[0] + " → " + p[1] + ":");
            System.out.println("  меньше пересадок (" + (hops.size() - 1) + "): " + String.join(" - ", hops));
            System.out.println("  короче всего (" + best.km() + " км): " + String.join(" - ", best.path()));
            double hours = best.km() / 70.0;
            System.out.printf("  в пути ~%.1f ч%n", hours);
        }
        System.out.println("Запросов: " + queries + ", найдено маршрутов: " + found);
    }
}
