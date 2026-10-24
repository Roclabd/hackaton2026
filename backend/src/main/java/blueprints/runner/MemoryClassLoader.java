package blueprints.runner;

import java.util.Map;

/**
 * Загружает классы из байткода в памяти. Новый загрузчик на каждый прогон =
 * статические поля программы каждый раз начинаются с нуля, как при запуске новой JVM.
 */
public final class MemoryClassLoader extends ClassLoader {

    private final Map<String, byte[]> classes;

    public MemoryClassLoader(Map<String, byte[]> classes) {
        super(MemoryClassLoader.class.getClassLoader());
        this.classes = classes;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = classes.get(name);
        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, bytes, 0, bytes.length);
    }
}
