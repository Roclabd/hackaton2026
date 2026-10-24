package blueprints.runner;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Извлекает вспомогательные метаданные JVM; сам stderr никогда не переписывает. */
final class JvmException {

    private static final Pattern HEADER = Pattern.compile("(?m)Exception in thread \"main\" ([^\\s:]+)(?:: ([^\\r\\n]*))?\\r?$");
    private static final Pattern TRACE = Pattern.compile("(?m)^(?:\\tat |\\tSuppressed: |Caused by: )");

    private JvmException() {}

    static RunResult.ExceptionInfo from(String stderr, int exitCode) {
        if (exitCode == 0) return null;
        Matcher header = HEADER.matcher(stderr);
        if (!header.find()) return null;
        String message = header.group(2);
        if (message != null) {
            Matcher trace = TRACE.matcher(stderr);
            int end = trace.find(header.end()) ? trace.start() : stderr.length();
            message = stderr.substring(header.start(2), end);
            // Последний перевод строки принадлежит печати заголовка/сообщения, остальные — самому сообщению.
            if (message.endsWith("\r\n")) message = message.substring(0, message.length() - 2);
            else if (message.endsWith("\n")) message = message.substring(0, message.length() - 1);
        }
        return new RunResult.ExceptionInfo(header.group(1), message);
    }
}
