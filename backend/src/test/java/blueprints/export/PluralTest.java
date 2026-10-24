package blueprints.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluralTest {

    @Test
    void russianForms() {
        assertEquals("1 тест", Plural.of(1, "тест", "теста", "тестов"));
        assertEquals("3 теста", Plural.of(3, "тест", "теста", "тестов"));
        assertEquals("11 тестов", Plural.of(11, "тест", "теста", "тестов"));
        assertEquals("41 тест", Plural.of(41, "тест", "теста", "тестов"));
        assertEquals("112 тестов", Plural.of(112, "тест", "теста", "тестов"));
    }
}
