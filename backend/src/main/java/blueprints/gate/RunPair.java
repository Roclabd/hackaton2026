package blueprints.gate;

import blueprints.oracle.Comparison;
import blueprints.runner.RunResult;

import java.util.List;

/**
 * Вход и два результата рядом — для показа расхождения или примера совпадения.
 *
 * @param differences что именно различается (stdout, stderr, exitCode, exception); пусто при совпадении
 */
public record RunPair(String input, String group, RunResult original, RunResult candidate,
                      List<Comparison.Difference> differences) {}
