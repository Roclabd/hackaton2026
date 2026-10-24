package blueprints.runner;

import blueprints.parse.SourceParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MainClassFinderTest {

    private static String find(String source) {
        return MainClassFinder.find(SourceParser.parseOrThrow(source));
    }

    @Test
    void prefersPublicClassWithMain() {
        assertEquals("App", find("""
                class Helper { public static void main(String[] a) {} }
                public class App { public static void main(String... args) {} }
                """));
    }

    @Test
    void anyTopLevelClassWithMainIfNoPublicOne() {
        assertEquals("Runner", find("""
                class Model {}
                class Runner { public static void main(String[] args) {} }
                """));
    }

    @Test
    void fallsBackToPublicThenFirstType() {
        assertEquals("Lib", find("class A {} public class Lib { void main() {} }"));
        assertEquals("A", find("class A {} class B {}"));
    }

    @Test
    void addsPackage() {
        assertEquals("shop.Main", find("package shop; public class Main { public static void main(String[] a) {} }"));
    }
}
