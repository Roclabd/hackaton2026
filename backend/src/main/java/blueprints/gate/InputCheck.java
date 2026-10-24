package blueprints.gate;

import blueprints.inputs.LabeledInput;
import blueprints.oracle.Comparison;
import blueprints.oracle.Oracle;
import blueprints.runner.RunResult;

/**
 * Три прогона на одном входе: оригинал дважды (детектор случайности) и кандидат один раз.
 * Любой из результатов может быть ещё null — прогон не закончен.
 */
record InputCheck(LabeledInput input, RunResult original, RunResult originalAgain, RunResult candidate) {

    enum Outcome { PENDING, MATCH, MISMATCH, UNKNOWN, NONDETERMINISTIC }

    InputCheck with(int slot, RunResult result) {
        return switch (slot) {
            case 0 -> new InputCheck(input, result, originalAgain, candidate);
            case 1 -> new InputCheck(input, original, result, candidate);
            default -> new InputCheck(input, original, originalAgain, result);
        };
    }

    boolean finished() {
        return original != null && originalAgain != null && candidate != null;
    }

    Outcome outcome() {
        if (!finished()) {
            return Outcome.PENDING;
        }
        Comparison repeat = Oracle.compare(original, originalAgain);
        if (repeat.outcome() == Comparison.Outcome.UNKNOWN) {
            return Outcome.UNKNOWN;
        }
        if (repeat.outcome() == Comparison.Outcome.DIFFERENT) {
            return Outcome.NONDETERMINISTIC; // оригинал сам с собой не совпал — вход в вердикт не идёт
        }
        return switch (comparison().outcome()) {
            case SAME -> Outcome.MATCH;
            case DIFFERENT -> Outcome.MISMATCH;
            case UNKNOWN -> Outcome.UNKNOWN;
        };
    }

    Comparison comparison() {
        return Oracle.compare(original, candidate);
    }

    boolean timedOut() {
        return finished() && (original.timedOut() || originalAgain.timedOut() || candidate.timedOut());
    }

    boolean truncated() {
        return finished() && (original.outputTruncated() || originalAgain.outputTruncated() || candidate.outputTruncated());
    }

    RunPair pair() {
        return new RunPair(input.text(), input.group(), original, candidate, comparison().differences());
    }
}
