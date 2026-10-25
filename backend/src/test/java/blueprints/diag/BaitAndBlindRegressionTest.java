package blueprints.diag;

import blueprints.parse.SourceParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Защита от ложных срабатываний и от потери находок.
 * <ul>
 *   <li>{@code bait/} — по файлу на шаблон правила: код похож на ошибку, но правильный. Ни одной находки.</li>
 *   <li>{@code blind/} — три монолита, написанные отдельно до настройки правил, с заложенными ошибками.
 *       Находки — ровно заложенные (кроме тех, что по шаблону не отличить от намеренной логики).</li>
 * </ul>
 * Файлы-приманки названы по номерам правил на момент написания: BaitR14 — пустой catch (такого правила нет,
 * он слишком шумный), BaitR15 — сравнение обёрток, сейчас это R14.
 */
class BaitAndBlindRegressionTest {

    private static final Path ROOT = Path.of("src/test/resources/regressions");

    static Stream<Path> baits() throws IOException {
        return Files.list(ROOT.resolve("bait")).filter(p -> p.toString().endsWith(".java")).sorted();
    }

    @ParameterizedTest
    @MethodSource("baits")
    void baitHasNoFindings(Path file) throws IOException {
        assertEquals(List.of(), findings(file), file.getFileName() + ": приманка не должна давать находок");
    }

    @Test
    void blindMonolithsGiveExactlyThePlantedFindings() throws IOException {
        assertEquals(List.of("R13@124", "R1@140", "R8@163", "R9@276"), findings(ROOT.resolve("blind/Mono1.java")));
        assertEquals(List.of("R7@109", "R10@127", "R2@149", "R4@162"), findings(ROOT.resolve("blind/Mono2.java")));
        assertEquals(List.of("R11@119", "R14@147", "R12@175"), findings(ROOT.resolve("blind/Mono3.java")));
    }

    /** «Правило@строка», по строкам. */
    private static List<String> findings(Path file) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        return new FindingsService().find(SourceParser.parseOrThrow(source), source).stream()
                .sorted(java.util.Comparator.comparingInt((Finding f) -> f.lines()[0]))
                .map(f -> f.rule() + "@" + f.lines()[0]).toList();
    }
}
