package blueprints.profile;

import java.util.List;

/**
 * Подбор модели роста по счётчикам: из {1, log n, n, n log n, n², n³} — та, у которой меньше ошибка подгонки
 * (см. {@link GrowthModel#error}). Дополнительно — наклон на лог-лог шкале: его легко проверить руками
 * (n² даёт наклон около 2, n — около 1).
 */
final class GrowthFit {

    /** Ошибки ближе этой считаем равными — тогда выбирается более простая модель. */
    private static final double TIE = 1e-9;

    private GrowthFit() {}

    static GrowthModel bestModel(List<Integer> sizes, List<Long> counts) {
        if (counts.stream().distinct().count() == 1) {
            return GrowthModel.CONSTANT; // одинаковые счётчики (в том числе нули) — от n не зависят
        }
        GrowthModel best = GrowthModel.CONSTANT;
        double bestError = best.error(sizes, counts);
        for (GrowthModel model : GrowthModel.values()) {
            double error = model.error(sizes, counts);
            if (error < bestError - TIE) {
                best = model;
                bestError = error;
            }
        }
        return best;
    }

    /** Наклон прямой МНК через точки (ln n, ln c). */
    static double logLogSlope(List<Integer> sizes, List<Long> counts) {
        int k = sizes.size();
        double mx = 0, my = 0;
        for (int i = 0; i < k; i++) {
            mx += Math.log(sizes.get(i)) / k;
            my += Math.log(Math.max(1, counts.get(i))) / k;
        }
        double num = 0, den = 0;
        for (int i = 0; i < k; i++) {
            double dx = Math.log(sizes.get(i)) - mx;
            num += dx * (Math.log(Math.max(1, counts.get(i))) - my);
            den += dx * dx;
        }
        return den == 0 ? 0 : num / den;
    }
}
