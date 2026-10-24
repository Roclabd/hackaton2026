package blueprints.export;

import blueprints.gate.GateService.CompletedCheck;
import blueprints.gate.VerdictStatus;
import blueprints.parse.ParseOutcome;
import blueprints.parse.SourceParser;
import blueprints.runner.MainClassFinder;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Завершённая проверка → мини-проект Maven с характеризационными тестами (задача B10).
 *
 * В архиве: pom.xml, проверенная версия кода (кандидат) в src/main/java, тесты на выводе эталона
 * в src/test/java, эталон для справки в reference/ и README.md.
 */
public final class JUnitExporter {

    /** Что показать в окне экспорта рядом с именем файла. */
    public static final String FRAMEWORK = "JUnit Jupiter " + MavenProjectWriter.JUNIT_VERSION;

    private JUnitExporter() {}

    /** Проверку нельзя экспортировать; текст — по-русски, для ответа 409. */
    public static final class NotExportable extends RuntimeException {
        NotExportable(String message) {
            super(message);
        }
    }

    /**
     * @throws NotExportable проверка не дала сравнения (не MATCHED и не MISMATCH) или в ней нет годных входов
     */
    public static JUnitExport export(String jobId, CompletedCheck check) {
        if (check.status() != VerdictStatus.MATCHED && check.status() != VerdictStatus.MISMATCH) {
            throw new NotExportable("Экспорт возможен только после проверки с итогом «совпало» или «расхождение», "
                    + "а у проверки " + jobId + " итог " + check.status());
        }
        CompilationUnit original = parse(check.originalSource(), "эталон");
        CompilationUnit candidate = parse(check.candidateSource(), "проверенная версия");
        // Запускается класс, найденный по эталону, — ровно так, как его запускала проверка.
        String mainClass = MainClassFinder.find(original);
        String simpleName = mainClass.substring(mainClass.lastIndexOf('.') + 1);

        CaseSelector.Selection selection = CaseSelector.select(check.runs());
        if (selection.cases().isEmpty()) {
            throw new NotExportable("В проверке " + jobId + " нет входов с известным и стабильным выводом эталона — "
                    + "тестировать нечего");
        }

        String testClass = simpleName + "CharacterizationTest";
        String mainFile = "src/main/java/" + packagePath(candidate) + fileName(candidate, simpleName);
        String testFile = "src/test/java/" + testClass + ".java";
        String referenceFile = "reference/" + fileName(original, simpleName);
        String origin = "Сгенерировано Java Blueprints по проверке " + jobId + " (итог " + check.status()
                + ", файл " + check.fileName() + ").";

        Map<String, String> files = new LinkedHashMap<>();
        files.put("README.md", MavenProjectWriter.readme(new MavenProjectWriter.ReadmeFacts(
                mainClass, mainFile, testFile, referenceFile, jobId, check.status(), selection)));
        files.put("pom.xml", MavenProjectWriter.pom(artifactId(simpleName)));
        files.put(mainFile, check.candidateSource());
        files.put(testFile, CharacterizationTestWriter.write(testClass, mainClass, origin, selection.cases()));
        files.put(MavenProjectWriter.JUNIT_PROPERTIES, MavenProjectWriter.junitProperties());
        files.put(referenceFile, check.originalSource());

        int skipped = selection.random() + selection.unknown() + selection.tooLarge();
        // Не Map.copyOf: он теряет порядок файлов, а README первым в архиве удобнее.
        return new JUnitExport(simpleName + "-tests.zip", Collections.unmodifiableMap(files), testFile,
                selection.cases().size(), skipped, CharacterizationTestWriter.method(selection.cases().getFirst()));
    }

    /** Архив с файлами экспорта; имена и содержимое — в UTF-8. */
    public static byte[] zip(JUnitExport export) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> file : export.files().entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось собрать архив", e);
        }
        return bytes.toByteArray();
    }

    private static CompilationUnit parse(String source, String what) {
        ParseOutcome parsed = new SourceParser().parse(source);
        return parsed.unit().orElseThrow(() -> new NotExportable("Не удалось разобрать " + what + " для экспорта"));
    }

    /** javac требует, чтобы публичный класс лежал в файле со своим именем; без публичного — по главному классу. */
    private static String fileName(CompilationUnit unit, String fallback) {
        return unit.getTypes().stream().filter(TypeDeclaration::isPublic).findFirst()
                .map(TypeDeclaration::getNameAsString).orElse(fallback) + ".java";
    }

    private static String packagePath(CompilationUnit unit) {
        return unit.getPackageDeclaration().map(p -> p.getNameAsString().replace('.', '/') + "/").orElse("");
    }

    private static String artifactId(String simpleName) {
        String slug = simpleName.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase().replaceAll("[^a-z0-9-]", "");
        return (slug.isEmpty() ? "program" : slug) + "-tests";
    }
}
