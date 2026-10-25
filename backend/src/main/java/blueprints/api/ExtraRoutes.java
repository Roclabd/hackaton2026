package blueprints.api;

import blueprints.project.ProjectStore;
import io.javalin.config.JavalinConfig;

/**
 * Маршруты модулей: проверка поведения (gate), находки (diag), экспорт тестов JUnit (export)
 * и замер роста (profile). Каждый модуль подключается здесь одной строкой.
 */
final class ExtraRoutes {

    private ExtraRoutes() {}

    static void register(JavalinConfig config, ProjectStore store) {
        blueprints.gate.GateRoutes.register(config, store);
        blueprints.diag.FindingRoutes.register(config, store);
        blueprints.export.ExportRoutes.register(config, store);
        blueprints.profile.ProfileRoutes.register(config, store);
        blueprints.heat.HeatRoutes.register(config, store);
    }
}
