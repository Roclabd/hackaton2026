package blueprints.runner;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Буфер вывода программы с потолком: бесконечная печать не съест память рабочего процесса.
 * Всё сверх лимита отбрасывается, а в конце текста ставится пометка.
 *
 * Обрезанный вывод нельзя сравнивать: разница могла остаться в отброшенном хвосте. Поэтому прогон
 * с обрезанным выводом помечается {@link RunResult#outputTruncated()}, и вердикт по нему не выносится.
 */
final class LimitedOutputStream extends OutputStream {

    static final int LIMIT = 1024 * 1024;
    static final String TRUNCATED_MARK = "\n[вывод обрезан: больше 1 МБ]";

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private boolean truncated;

    @Override
    public synchronized void write(int b) {
        if (bytes.size() < LIMIT) {
            bytes.write(b);
        } else {
            truncated = true;
        }
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) {
        int room = LIMIT - bytes.size();
        if (len > room) {
            truncated = true;
        }
        bytes.write(b, off, Math.max(0, Math.min(len, room)));
    }

    synchronized boolean truncated() {
        return truncated;
    }

    synchronized String text() {
        String text = bytes.toString(StandardCharsets.UTF_8);
        return truncated ? text + TRUNCATED_MARK : text;
    }
}
