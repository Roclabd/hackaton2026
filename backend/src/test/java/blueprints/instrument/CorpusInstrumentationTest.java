package blueprints.instrument;

import blueprints.parse.SourceParser;
import blueprints.runner.CompileResult;
import blueprints.runner.InMemoryCompiler;
import blueprints.runner.MainClassFinder;
import blueprints.runner.ProgramRunner;
import blueprints.runner.RunResult;
import blueprints.runner.WorkerProtocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Худший случай для вставки: «изменены» все строки файла — счётчик почти перед каждым оператором.
 * Копия каждого файла корпуса и регрессионных примеров, который компилируется сам, должна компилироваться и со счётчиками.
 */
class CorpusInstrumentationTest {

    private static final InMemoryCompiler COMPILER = new InMemoryCompiler();

    static Stream<Path> files() throws IOException {
        return Stream.of(Path.of("../corpus"), Path.of("src/test/resources/regressions"))
                .filter(Files::isDirectory)
                .flatMap(CorpusInstrumentationTest::javaFiles);
    }

    private static Stream<Path> javaFiles(Path root) {
        try {
            return Files.walk(root).filter(p -> p.toString().endsWith(".java")).sorted().toList().stream();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @ParameterizedTest
    @MethodSource("files")
    void everyLineInstrumentedStillCompiles(Path file) throws Exception {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        var parsed = new SourceParser().parse(source);
        if (parsed.unit().isEmpty() || parsed.unit().get().getTypes().isEmpty()) {
            return; // файл-проба нарочно не разбирается — вставлять некуда
        }
        String fileClass = MainClassFinder.find(parsed.unit().get());
        if (!COMPILER.compile(fileClass, source).ok()) {
            return; // проба нарочно не компилируется
        }
        SortedSet<Integer> all = new TreeSet<>(IntStream.rangeClosed(1, (int) source.lines().count()).boxed().toList());
        var copy = CoverageInstrumenter.instrument(source, all);
        if (copy.isEmpty()) {
            return; // в файле нет операторов
        }
        CompileResult compiled = COMPILER.compile(fileClass, copy.get().source());
        assertTrue(compiled.ok(), file + ": " + compiled.messages());
    }

    @Test
    void miniLangWithCountersEverywhereBehavesTheSame() throws Exception {
        String source = Files.readString(Path.of("../corpus/ours/MiniLang/MiniLang.java"), StandardCharsets.UTF_8);
        SortedSet<Integer> all = new TreeSet<>(IntStream.rangeClosed(1, (int) source.lines().count()).boxed().toList());
        InstrumentedSource copy = CoverageInstrumenter.instrument(source, all).orElseThrow();
        ProgramRunner runner = new ProgramRunner();
        RunResult original = runner.runOne(program(source), "", Duration.ofSeconds(10));
        RunResult measured = runner.runOne(program(copy.source()), "", Duration.ofSeconds(10));
        assertFalse(original.stdout().isBlank());
        assertEquals(original.stdout(), measured.stdout());
        assertEquals(original.stderr(), measured.stderr());
        assertEquals(original.exitCode(), measured.exitCode());
        assertTrue(Arrays.stream(measured.coverageHits()).filter(h -> h > 0).count() > 100,
                "выполнилось больше сотни операторов из " + copy.counters());
    }

    private static WorkerProtocol.Program program(String source) {
        CompileResult compiled = COMPILER.compile("MiniLang", source);
        assertTrue(compiled.ok(), compiled.messages().toString());
        return new WorkerProtocol.Program("p", "MiniLang", compiled.classes());
    }
}
