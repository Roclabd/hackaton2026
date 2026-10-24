package blueprints.parse;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;

import java.util.List;

/**
 * Разбор исходного текста Java в дерево разбора (JavaParser).
 *
 * Уровень языка выставлен явно — JAVA_25: иначе JavaParser может не принять свежий синтаксис.
 * Комментарии сохраняются в дереве, чтобы при печати кода они не терялись.
 *
 * Потокобезопасен: экземпляр JavaParser хранит внутреннее состояние разбора и не может
 * обслуживать параллельные запросы (аудит v2: 17 из 800 параллельных разборов корректны).
 * Поэтому на каждый разбор создаётся свой — это дёшево по сравнению с самим разбором.
 */
public final class SourceParser {

    public ParseOutcome parse(String source) {
        ParseResult<CompilationUnit> result = newParser().parse(source);
        if (result.isSuccessful() && result.getResult().isPresent()) {
            return ParseOutcome.success(result.getResult().get());
        }
        return ParseOutcome.failure(result.getProblems().stream().map(SourceParser::toError).toList());
    }

    /** Проверка текста, которым заменят условие узла: это одно Java-выражение? Пусто — да. */
    public List<ParseError> checkExpression(String text) {
        return problems(newParser().parseExpression(text));
    }

    /** Проверка текста, которым заменят оператор узла: это один Java-оператор? Пусто — да. */
    public List<ParseError> checkStatement(String text) {
        return problems(newParser().parseStatement(text));
    }

    private static List<ParseError> problems(ParseResult<?> result) {
        if (result.isSuccessful()) {
            return List.of();
        }
        List<ParseError> errors = result.getProblems().stream().map(SourceParser::toError).toList();
        return errors.isEmpty() ? List.of(new ParseError(1, 1, "Не разбирается")) : errors;
    }

    private static JavaParser newParser() {
        ParserConfiguration configuration = new ParserConfiguration()
                .setLanguageLevel(LanguageLevel.JAVA_25)
                .setAttributeComments(true);
        return new JavaParser(configuration);
    }

    private static ParseError toError(Problem problem) {
        int line = 1;
        int column = 1;
        if (problem.getLocation().isPresent() && problem.getLocation().get().getBegin().getRange().isPresent()) {
            var begin = problem.getLocation().get().getBegin().getRange().get().begin;
            line = begin.line;
            column = begin.column;
        }
        return new ParseError(line, column, problem.getMessage());
    }

    /** Для тестов и утилит: разобрать или упасть с понятным сообщением. */
    public static CompilationUnit parseOrThrow(String source) {
        ParseOutcome outcome = new SourceParser().parse(source);
        return outcome.unit().orElseThrow(() -> new IllegalArgumentException("Код не разбирается: " + List.copyOf(outcome.errors())));
    }
}
