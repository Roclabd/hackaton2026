package blueprints.commands;

import com.github.javaparser.GeneratedJavaParserConstants;
import com.github.javaparser.JavaParser;
import com.github.javaparser.JavaToken;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Проверка «правка не задела ничего вне узла».
 *
 * Текст правки сам по себе разбирается как один оператор или выражение, но в файле соседствует с чужим кодом.
 * Строчный комментарий в конце правки (`a = 5; // поправил`) закомментирует остаток строки, например `b = 2;`;
 * файл при этом остаётся валидным и компилируется — отличить такую правку по ошибкам нельзя.
 *
 * Способ: сравниваем последовательности лексем без пробелов и комментариев. Ожидаем, что в новом файле идут подряд:
 * лексемы старого файла до узла, лексемы самого текста правки, лексемы старого файла после узла.
 * Любое расхождение (проглоченный комментарием код, незакрытый блочный комментарий) даёт другую последовательность.
 * Поиск `//` в строке не годится: он ложно сработает на `"http://…"` в строковом литерале и пропустит блочный комментарий.
 * Закрытый блочный комментарий внутри текста правки лексем не добавляет, поэтому такая правка допустима.
 */
final class RestOfFileGuard {

    private RestOfFileGuard() {}

    /**
     * @param oldUnit  дерево файла до правки
     * @param oldRange диапазон заменённого узла в старом файле
     * @param newUnit  дерево файла после подстановки
     * @param text     текст правки
     * @param asCondition текст — условие (выражение), иначе — оператор
     * @return true, если вне узла лексемы те же
     */
    static boolean restUnchanged(CompilationUnit oldUnit, Range oldRange, CompilationUnit newUnit,
                                 String text, boolean asCondition) {
        Optional<List<String>> textTokens = tokensOfText(text, asCondition);
        if (textTokens.isEmpty()) {
            return false;
        }
        List<String> expected = new ArrayList<>();
        List<String> suffix = new ArrayList<>();
        for (JavaToken token : codeTokens(oldUnit)) {
            Range range = token.getRange().orElseThrow();
            if (range.end.isBefore(oldRange.begin)) {
                expected.add(token.getText());
            } else if (range.begin.isAfter(oldRange.end)) {
                suffix.add(token.getText());
            }
        }
        expected.addAll(textTokens.get());
        expected.addAll(suffix);
        return expected.equals(codeTokens(newUnit).stream().map(JavaToken::getText).toList());
    }

    private static Optional<List<String>> tokensOfText(String text, boolean asCondition) {
        JavaParser parser = new JavaParser(new ParserConfiguration().setLanguageLevel(LanguageLevel.JAVA_25));
        Optional<? extends Node> parsed = asCondition
                ? parser.parseExpression(text).getResult()
                : parser.parseStatement(text).getResult();
        return parsed.flatMap(Node::getTokenRange)
                .map(range -> codeTokens(range).stream().map(JavaToken::getText).toList());
    }

    private static List<JavaToken> codeTokens(CompilationUnit unit) {
        return codeTokens(unit.getTokenRange().orElseThrow());
    }

    private static List<JavaToken> codeTokens(Iterable<JavaToken> tokens) {
        List<JavaToken> result = new ArrayList<>();
        for (JavaToken token : tokens) {
            JavaToken.Category category = token.getCategory();
            if (!category.isWhitespaceOrComment() && token.getKind() != GeneratedJavaParserConstants.EOF) {
                result.add(token);
            }
        }
        return result;
    }
}
