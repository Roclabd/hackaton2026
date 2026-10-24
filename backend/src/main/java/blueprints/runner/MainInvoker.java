package blueprints.runner;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Вызывает main так же, как это делает команда {@code java}: сначала {@code static main(String[])},
 * затем варианты JDK 25 без static и без аргументов. Исключение программы пробрасывается как есть.
 */
final class MainInvoker {

    /** Сообщение, если запускать нечего. Текст как у команды java — оригинал и кандидат получат одинаковый. */
    static final class LaunchError extends Exception {
        LaunchError(String message) {
            super(message);
        }
    }

    private MainInvoker() {}

    static void invoke(ClassLoader loader, String mainClass) throws Throwable {
        Class<?> type;
        try {
            type = Class.forName(mainClass, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            throw new LaunchError("Error: Could not find or load main class " + mainClass
                    + "\nCaused by: " + e);
        }
        Method main = findMain(type);
        if (main == null) {
            throw new LaunchError("Error: Main method not found in class " + mainClass
                    + ", please define the main method as:\n   public static void main(String[] args)");
        }
        main.setAccessible(true);
        Object target = Modifier.isStatic(main.getModifiers()) ? null : newInstance(type);
        Object[] args = main.getParameterCount() == 1 ? new Object[]{new String[0]} : new Object[0];
        try {
            main.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static Method findMain(Class<?> type) {
        Method withArgs = method(type, String[].class);
        if (withArgs != null && Modifier.isStatic(withArgs.getModifiers())) {
            return withArgs;
        }
        Method noArgs = method(type);
        if (noArgs != null && Modifier.isStatic(noArgs.getModifiers())) {
            return noArgs;
        }
        return withArgs != null ? withArgs : noArgs;
    }

    private static Method method(Class<?> type, Class<?>... parameters) {
        try {
            Method m = type.getDeclaredMethod("main", parameters);
            return m.getReturnType() == void.class && !Modifier.isPrivate(m.getModifiers()) ? m : null;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Object newInstance(Class<?> type) throws Throwable {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
