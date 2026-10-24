package blueprints.project;

import blueprints.graph.Graph;
import blueprints.graph.ProjectMapBuilder;
import blueprints.parse.ParseOutcome;
import blueprints.parse.SourceParser;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранилище загруженных монолитов в памяти процесса. Для хакатона базы данных не нужно:
 * перезапуск сервера = чистый лист.
 */
public final class ProjectStore {

    private final Map<String, Project> projects = new ConcurrentHashMap<>();
    private final SourceParser parser = new SourceParser();
    private final ProjectMapBuilder mapBuilder = new ProjectMapBuilder();
    private final CompileCheck compileCheck = new CompileCheck();

    public Project create(String fileName, String source) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        ParseOutcome outcome = parser.parse(source);
        Graph map = outcome.unit().map(mapBuilder::build).orElse(Graph.empty());
        Project project = new Project(id, 1, fileName, source, outcome.errors(), compileErrors(outcome, source),
                outcome.unit(), map);
        projects.put(id, project);
        return project;
    }

    /** Компилируем только разобранный код: синтаксические ошибки и так уже показаны. */
    private List<CompileError> compileErrors(ParseOutcome outcome, String source) {
        return outcome.unit().map(unit -> compileCheck.errors(unit, source)).orElse(List.of());
    }

    /**
     * Новый текст целиком, сделанный к версии expectedVersion.
     * Если он не разбирается — схема и дерево остаются от последней рабочей версии.
     *
     * Разбор идёт до блокировки (он медленный), а сравнение версии и сохранение — внутри одного
     * атомарного compute: из двух правок к одной версии проходит ровно одна, вторая получает конфликт.
     *
     * @throws VersionConflictException версия на сервере уже не expectedVersion
     * @throws ProjectNotFoundException проекта нет
     */
    public Project updateSource(String id, int expectedVersion, String source) {
        ParseOutcome outcome = parser.parse(source);
        Graph newMap = outcome.unit().map(mapBuilder::build).orElse(null);
        List<CompileError> compileErrors = compileErrors(outcome, source);

        Project updated = projects.computeIfPresent(id, (key, current) -> {
            if (current.version() != expectedVersion) {
                throw new VersionConflictException(current.version(), expectedVersion);
            }
            int version = current.version() + 1;
            return newMap != null
                    ? new Project(id, version, current.fileName(), source, outcome.errors(), compileErrors,
                            outcome.unit(), newMap)
                    : new Project(id, version, current.fileName(), source, outcome.errors(), compileErrors,
                            current.lastGood(), current.map());
        });
        if (updated == null) {
            throw new ProjectNotFoundException(id);
        }
        return updated;
    }

    public Optional<Project> find(String id) {
        return Optional.ofNullable(projects.get(id));
    }
}
