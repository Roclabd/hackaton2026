package blueprints.gate;

import com.fasterxml.jackson.annotation.JsonValue;

/** Этап проверки — для строки состояния в интерфейсе. В JSON строчными: "compile", "run"… */
public enum Phase {
    COMPILE, RUN, COMPARE, DONE;

    @JsonValue
    public String json() {
        return name().toLowerCase();
    }
}
