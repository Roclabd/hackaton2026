package blueprints.gate;

/** Итог проверки. BEHAVIOR_CHANGE — никогда не «зелёный»: изменение поведения решает человек. */
public enum VerdictStatus { RUNNING, MATCHED, MISMATCH, INCOMPLETE, BUILD_ERROR, BEHAVIOR_CHANGE }
