package blueprints.api;

import blueprints.project.ProjectStore;
import io.javalin.config.JavalinConfig;

/**
 * Маршруты модулей, которые пишутся параллельно: проверка поведения (gate) и находки (diag).
 * Подключаются здесь одной строкой, когда модуль готов, — чтобы до этого сборка не ломалась.
 */
final class ExtraRoutes {

    private ExtraRoutes() {}

    static void register(JavalinConfig config, ProjectStore store) {
        blueprints.gate.GateRoutes.register(config, store);
        blueprints.diag.FindingRoutes.register(config, store);
    }
}
