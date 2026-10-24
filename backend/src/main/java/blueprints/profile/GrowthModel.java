package blueprints.profile;

import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * Модели роста, из которых выбирается ответ. Порядок — от простой к сложной:
 * при равной ошибке подгонки побеждает более простая.
 */
enum GrowthModel {
    CONSTANT("1", n -> 1),
    LOG("log n", Math::log),
    LINEAR("n", n -> n),
    N_LOG_N("n log n", n -> n * Math.log(n)),
    QUADRATIC("n²", n -> n * n),
    CUBIC("n³", n -> n * n * n);

    private final String label;
    private final DoubleUnaryOperator f;

    GrowthModel(String label, DoubleUnaryOperator f) {
        this.label = label;
        this.f = f;
    }

    String label() {
        return label;
    }

    /**
     * Ошибка подгонки «счётчик ≈ a · f(n)» с лучшим a. Считаем в логарифмах: ln c − ln f(n) = ln a + шум,
     * поэтому ошибка — разброс остатков вокруг их среднего. Так одинаково весят малые и большие n
     * (иначе ошибку определяла бы одна последняя точка).
     */
    double error(List<Integer> sizes, List<Long> counts) {
        double[] residuals = new double[sizes.size()];
        double mean = 0;
        for (int i = 0; i < residuals.length; i++) {
            residuals[i] = Math.log(Math.max(1, counts.get(i))) - Math.log(f.applyAsDouble(sizes.get(i)));
            mean += residuals[i] / residuals.length;
        }
        double error = 0;
        for (double r : residuals) {
            error += (r - mean) * (r - mean);
        }
        return error;
    }
}
