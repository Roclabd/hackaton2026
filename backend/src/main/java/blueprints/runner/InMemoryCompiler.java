package blueprints.runner;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Компиляция одного исходника Java в байткод прямо в памяти, без файлов на диске.
 *
 * Замер (прототипы/RunnerProbe): ~300 мс первый раз, ~100 мс на прогретом компиляторе для 1100 строк.
 * Экземпляр можно переиспользовать — компилятор JDK потокобезопасен на уровне отдельных задач.
 */
public final class InMemoryCompiler {

    private static final List<String> OPTIONS = List.of("-encoding", "UTF-8", "-proc:none", "-g");

    private final JavaCompiler javac;

    public InMemoryCompiler() {
        this.javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) {
            throw new IllegalStateException("Нет компилятора: сервер запущен на JRE, а нужен JDK 25");
        }
    }

    /**
     * @param mainClassName имя публичного класса (от него зависит имя файла для javac)
     * @return результат: байткод всех классов или сообщения компилятора
     */
    public CompileResult compile(String mainClassName, String source) {
        Map<String, BytecodeFile> output = new LinkedHashMap<>();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager standard = javac.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8);

        JavaFileManager inMemory = new ForwardingJavaFileManager<>(standard) {
            @Override
            public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
                BytecodeFile file = new BytecodeFile(className);
                output.put(className, file);
                return file;
            }
        };

        boolean ok = javac.getTask(null, inMemory, diagnostics, OPTIONS, null,
                List.of(new SourceFile(mainClassName, source))).call();

        List<CompileResult.Message> messages = diagnostics.getDiagnostics().stream()
                .map(d -> new CompileResult.Message(d.getKind().name(), (int) d.getLineNumber(), (int) d.getColumnNumber(),
                        d.getMessage(Locale.ROOT)))
                .toList();
        if (!ok) {
            return CompileResult.failure(messages);
        }
        Map<String, byte[]> classes = new LinkedHashMap<>();
        output.forEach((name, file) -> classes.put(name, file.bytes()));
        return CompileResult.success(classes, messages);
    }

    /** Исходник, который javac читает из строки. */
    private static final class SourceFile extends SimpleJavaFileObject {
        private final String code;

        SourceFile(String className, String code) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    /** Файл .class, который javac пишет в память. */
    private static final class BytecodeFile extends SimpleJavaFileObject {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        BytecodeFile(String className) {
            super(URI.create("mem:///" + className.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
        }

        @Override
        public OutputStream openOutputStream() {
            return bytes;
        }

        byte[] bytes() {
            return bytes.toByteArray();
        }
    }
}
