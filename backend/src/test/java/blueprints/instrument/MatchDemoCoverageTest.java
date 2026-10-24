package blueprints.instrument;

import blueprints.diag.Finding;
import blueprints.diag.FindingsService;
import blueprints.inputs.InputGenerator;
import blueprints.inputs.LabeledInput;
import blueprints.parse.SourceParser;
import blueprints.runner.InMemoryCompiler;
import blueprints.runner.ProgramRunner;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Демо питча: заплатка R1 к MatchDemo на входах проверки выполняется целиком и не меньше чем на 3 входах. */
class MatchDemoCoverageTest {

    @Test
    void patchIsCoveredByGeneratedInputs() throws Exception {
        String source = Files.readString(Path.of("../corpus/ours/MatchDemo/MatchDemo.java"), StandardCharsets.UTF_8);
        List<Finding> findings = new FindingsService().find(SourceParser.parseOrThrow(source), source);
        String patched = findings.getFirst().patches().getFirst().source();
        List<String> inputs = new InputGenerator().generate("MatchDemo.java", source).stream().map(LabeledInput::text).toList();

        ChangedCoverage coverage = new ChangedCoverageMeter(new InMemoryCompiler(), new ProgramRunner(), 2)
                .measure(source, patched, "MatchDemo", inputs, Duration.ofSeconds(5)).summary();
        System.out.println("[замер] MatchDemo + R1: " + coverage);

        assertTrue(coverage.lines() > 3, "в заплатке несколько операторов: " + coverage);
        assertEquals(coverage.lines(), coverage.covered(), coverage.toString());
        assertTrue(coverage.minHits() >= 3, coverage.toString());
    }
}
