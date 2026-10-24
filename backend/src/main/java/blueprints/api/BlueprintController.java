package blueprints.api;

import blueprints.commands.EditResult;
import blueprints.commands.EditStatementCommand;
import blueprints.graph.Blueprint;
import blueprints.project.Blueprints;
import blueprints.project.Project;
import blueprints.project.ProjectNotFoundException;
import blueprints.project.ProjectStore;
import blueprints.runner.InMemoryCompiler;
import io.javalin.http.Context;

import java.util.LinkedHashMap;
import java.util.Map;

/** Чертёж метода (уровень 1) и правка узла на нём. */
final class BlueprintController {

    private final ProjectStore store;
    private final Blueprints blueprints = new Blueprints();
    private final EditStatementCommand editStatement;

    BlueprintController(ProjectStore store) {
        this.store = store;
        this.editStatement = new EditStatementCommand(store, new InMemoryCompiler());
    }

    /** GET /api/projects/{id}/methods/{methodId} */
    void method(Context ctx) {
        Project project = find(ctx);
        String methodId = ctx.pathParam("methodId");
        Blueprint blueprint = blueprints.forMethod(project, methodId).orElseThrow(() -> project.errors().isEmpty()
                ? new ApiException(404, "Метод не найден: " + methodId)
                : new ApiException(409, "Код сейчас не разбирается — чертёж метода недоступен"));
        ctx.json(blueprint.view());
    }

    /** POST /api/projects/{id}/commands — тело {type: "editStatement", nodeId, text, baseVersion}. */
    void command(Context ctx) {
        CommandRequest request = Bodies.read(ctx, CommandRequest.class);
        if (!"editStatement".equals(request.type())) {
            throw ApiException.badRequest("Неизвестная команда: " + request.type() + ". Поддерживается editStatement");
        }
        if (request.nodeId() == null || request.baseVersion() == null) {
            throw ApiException.badRequest("Нужны поля nodeId и baseVersion");
        }
        EditResult result = editStatement.apply(ctx.pathParam("id"), request.nodeId(), request.text(), request.baseVersion());
        ctx.json(response(result));
    }

    private static Map<String, Object> response(EditResult result) {
        Project project = result.project();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", result.ok());
        body.put("id", project.id());
        body.put("version", project.version());
        body.put("fileName", project.fileName());
        body.put("source", project.source());
        body.put("errors", project.errors());
        body.put("map", project.map());
        body.put("methodId", result.methodId());
        body.put("blueprint", result.blueprint() == null ? null : result.blueprint().view());
        body.put("idMap", result.idMap());
        body.put("compileErrors", result.compileErrors());
        return body;
    }

    private Project find(Context ctx) {
        String id = ctx.pathParam("id");
        return store.find(id).orElseThrow(() -> new ProjectNotFoundException(id));
    }

    record CommandRequest(String type, String nodeId, String text, Integer baseVersion) {}
}
