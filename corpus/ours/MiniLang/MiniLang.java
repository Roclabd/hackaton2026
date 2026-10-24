import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MiniLang — небольшой динамически типизированный скриптовый язык в одном файле.
 *
 * Возможности: числа, строки, булевы значения, nil, списки, функции первого
 * класса, замыкания и лямбды, циклы while / for, составное присваивание
 * (+=, -=, *=, /=) и набор встроенных функций.
 *
 * Запуск:
 *   javac MiniLang.java
 *   java MiniLang              — демонстрационная программа
 *   java MiniLang script.mini  — выполнить файл
 */
public final class MiniLang {

    // ---------- Токены ----------

    enum Tok {
        LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE, LEFT_BRACKET, RIGHT_BRACKET,
        COMMA, SEMICOLON,
        MINUS, PLUS, SLASH, STAR, PERCENT,
        MINUS_EQUAL, PLUS_EQUAL, SLASH_EQUAL, STAR_EQUAL,
        BANG, BANG_EQUAL, EQUAL, EQUAL_EQUAL,
        GREATER, GREATER_EQUAL, LESS, LESS_EQUAL,
        IDENTIFIER, STRING, NUMBER,
        AND, ELSE, FALSE, FOR, FUN, IF, NIL, OR,
        PRINT, RETURN, TRUE, VAR, WHILE,
        EOF
    }

    static final class Token {
        final Tok type; final String lexeme; final Object literal; final int line;
        Token(Tok type, String lexeme, Object literal, int line) {
            this.type = type; this.lexeme = lexeme; this.literal = literal; this.line = line;
        }
    }

    // ---------- Ошибки и управляющие сигналы ----------

    static final class SyntaxError extends RuntimeException {
        final int line;
        SyntaxError(int line, String message) { super(message); this.line = line; }
    }

    static final class RuntimeError extends RuntimeException {
        final Token token;
        RuntimeError(Token token, String message) { super(message); this.token = token; }
    }

    /** Сигнал return не собирает стек: он используется для обычного потока управления. */
    static final class ReturnSignal extends RuntimeException {
        final Object value;
        ReturnSignal(Object value) { super(null, null, false, false); this.value = value; }
    }

    // ---------- Лексер ----------

    static final class Lexer {
        private static final Map<String, Tok> KEYWORDS = new HashMap<>();

        static {
            for (Tok t : new Tok[] {Tok.AND, Tok.ELSE, Tok.FALSE, Tok.FOR, Tok.FUN, Tok.IF, Tok.NIL,
                    Tok.OR, Tok.PRINT, Tok.RETURN, Tok.TRUE, Tok.VAR, Tok.WHILE}) {
                KEYWORDS.put(t.name().toLowerCase(), t);
            }
        }

        private final String src;
        private final List<Token> tokens = new ArrayList<>();
        private int start;
        private int current;
        private int line = 1;

        Lexer(String src) {
            this.src = src;
        }

        List<Token> scan() {
            while (!atEnd()) {
                start = current;
                scanToken();
            }
            tokens.add(new Token(Tok.EOF, "", null, line));
            return tokens;
        }

        private void scanToken() {
            char c = advance();
            switch (c) {
                case '(': add(Tok.LEFT_PAREN); break;
                case ')': add(Tok.RIGHT_PAREN); break;
                case '{': add(Tok.LEFT_BRACE); break;
                case '}': add(Tok.RIGHT_BRACE); break;
                case '[': add(Tok.LEFT_BRACKET); break;
                case ']': add(Tok.RIGHT_BRACKET); break;
                case ',': add(Tok.COMMA); break;
                case ';': add(Tok.SEMICOLON); break;
                case '%': add(Tok.PERCENT); break;
                case '-': add(match('=') ? Tok.MINUS_EQUAL : Tok.MINUS); break;
                case '+': add(match('=') ? Tok.PLUS_EQUAL : Tok.PLUS); break;
                case '*': add(match('=') ? Tok.STAR_EQUAL : Tok.STAR); break;
                case '!': add(match('=') ? Tok.BANG_EQUAL : Tok.BANG); break;
                case '=': add(match('=') ? Tok.EQUAL_EQUAL : Tok.EQUAL); break;
                case '<': add(match('=') ? Tok.LESS_EQUAL : Tok.LESS); break;
                case '>': add(match('=') ? Tok.GREATER_EQUAL : Tok.GREATER); break;
                case '/':
                    if (match('/')) {
                        while (peek() != '\n' && !atEnd()) advance();
                    } else {
                        add(match('=') ? Tok.SLASH_EQUAL : Tok.SLASH);
                    }
                    break;
                case ' ': case '\r': case '\t': break;
                case '\n': line++; break;
                case '"': string(); break;
                default:
                    if (isDigit(c)) number();
                    else if (isAlpha(c)) identifier();
                    else throw new SyntaxError(line, "Неожиданный символ '" + c + "'");
            }
        }

        private void string() {
            StringBuilder sb = new StringBuilder();
            while (peek() != '"') {
                if (atEnd()) throw new SyntaxError(line, "Незакрытая строка");
                char c = advance();
                if (c == '\n') line++;
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = atEnd() ? '\0' : advance();
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    default: throw new SyntaxError(line, "Неизвестная escape-последовательность \\" + e);
                }
            }
            advance();
            add(Tok.STRING, sb.toString());
        }

        private void number() {
            while (isDigit(peek())) advance();
            if (peek() == '.' && current + 1 < src.length() && isDigit(src.charAt(current + 1))) {
                advance();
                while (isDigit(peek())) advance();
            }
            add(Tok.NUMBER, Double.parseDouble(src.substring(start, current)));
        }

        private void identifier() {
            while (isAlpha(peek()) || isDigit(peek())) advance();
            add(KEYWORDS.getOrDefault(src.substring(start, current), Tok.IDENTIFIER));
        }

        private boolean match(char expected) {
            if (atEnd() || src.charAt(current) != expected) return false;
            current++;
            return true;
        }

        private char peek() { return atEnd() ? '\0' : src.charAt(current); }
        private char advance() { return src.charAt(current++); }
        private boolean atEnd() { return current >= src.length(); }
        private boolean isDigit(char c) { return c >= '0' && c <= '9'; }
        private boolean isAlpha(char c) { return Character.isLetter(c) || c == '_'; }

        private void add(Tok type) { add(type, null); }

        private void add(Tok type, Object literal) {
            tokens.add(new Token(type, src.substring(start, current), literal, line));
        }
    }

    // ---------- AST: выражения ----------

    abstract static class Expr {
    }

    static final class Assign extends Expr {
        final Token name; final Expr value;
        Assign(Token name, Expr value) { this.name = name; this.value = value; }
    }

    static final class Binary extends Expr {
        final Expr left; final Token op; final Expr right;
        Binary(Expr left, Token op, Expr right) { this.left = left; this.op = op; this.right = right; }
    }

    static final class Call extends Expr {
        final Expr callee; final Token paren; final List<Expr> args;
        Call(Expr callee, Token paren, List<Expr> args) { this.callee = callee; this.paren = paren; this.args = args; }
    }

    static final class Index extends Expr {
        final Expr object; final Token bracket; final Expr index;
        Index(Expr object, Token bracket, Expr index) { this.object = object; this.bracket = bracket; this.index = index; }
    }

    static final class IndexSet extends Expr {
        final Expr object; final Token bracket; final Expr index; final Expr value;
        IndexSet(Expr object, Token bracket, Expr index, Expr value) {
            this.object = object; this.bracket = bracket; this.index = index; this.value = value;
        }
    }

    static final class Literal extends Expr {
        final Object value;
        Literal(Object value) { this.value = value; }
    }

    static final class Logical extends Expr {
        final Expr left; final Token op; final Expr right;
        Logical(Expr left, Token op, Expr right) { this.left = left; this.op = op; this.right = right; }
    }

    static final class Unary extends Expr {
        final Token op; final Expr right;
        Unary(Token op, Expr right) { this.op = op; this.right = right; }
    }

    static final class Variable extends Expr {
        final Token name;
        Variable(Token name) { this.name = name; }
    }

    static final class ListLit extends Expr {
        final List<Expr> items;
        ListLit(List<Expr> items) { this.items = items; }
    }

    static final class Lambda extends Expr {
        final List<Token> params; final List<Stmt> body;
        Lambda(List<Token> params, List<Stmt> body) { this.params = params; this.body = body; }
    }

    // ---------- AST: инструкции ----------

    abstract static class Stmt {
    }

    static final class ExprStmt extends Stmt {
        final Expr expression;
        ExprStmt(Expr expression) { this.expression = expression; }
    }

    static final class PrintStmt extends Stmt {
        final Expr expression;
        PrintStmt(Expr expression) { this.expression = expression; }
    }

    static final class VarStmt extends Stmt {
        final Token name; final Expr initializer;
        VarStmt(Token name, Expr initializer) { this.name = name; this.initializer = initializer; }
    }

    static final class BlockStmt extends Stmt {
        final List<Stmt> statements;
        BlockStmt(List<Stmt> statements) { this.statements = statements; }
    }

    static final class IfStmt extends Stmt {
        final Expr condition; final Stmt thenBranch; final Stmt elseBranch;
        IfStmt(Expr condition, Stmt thenBranch, Stmt elseBranch) {
            this.condition = condition; this.thenBranch = thenBranch; this.elseBranch = elseBranch;
        }
    }

    /** for раскрывается в while; increment выполняется после каждой итерации. */
    static final class WhileStmt extends Stmt {
        final Expr condition; final Stmt body; final Expr increment;
        WhileStmt(Expr condition, Stmt body, Expr increment) { this.condition = condition; this.body = body; this.increment = increment; }
    }

    static final class FunctionStmt extends Stmt {
        final Token name; final List<Token> params; final List<Stmt> body;
        FunctionStmt(Token name, List<Token> params, List<Stmt> body) { this.name = name; this.params = params; this.body = body; }
    }

    static final class ReturnStmt extends Stmt {
        final Expr value;
        ReturnStmt(Expr value) { this.value = value; }
    }

    // ---------- Парсер (рекурсивный спуск) ----------

    static final class Parser {
        /** Уровни приоритета бинарных операторов, от низшего к высшему. */
        private static final Tok[][] LEVELS = {
            {Tok.OR},
            {Tok.AND},
            {Tok.BANG_EQUAL, Tok.EQUAL_EQUAL},
            {Tok.GREATER, Tok.GREATER_EQUAL, Tok.LESS, Tok.LESS_EQUAL},
            {Tok.MINUS, Tok.PLUS},
            {Tok.SLASH, Tok.STAR, Tok.PERCENT},
        };

        private final List<Token> tokens;
        private int current;
        private int functionDepth;

        Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        List<Stmt> parse() {
            List<Stmt> statements = new ArrayList<>();
            while (!atEnd()) statements.add(declaration());
            return statements;
        }

        private Stmt declaration() {
            if (check(Tok.FUN) && checkNext(Tok.IDENTIFIER)) {
                advance();
                Token name = advance();
                List<Token> params = parameters();
                consume(Tok.LEFT_BRACE, "Ожидалась '{' перед телом функции");
                return new FunctionStmt(name, params, functionBody());
            }
            if (match(Tok.VAR)) return varDeclaration();
            return statement();
        }

        private List<Token> parameters() {
            consume(Tok.LEFT_PAREN, "Ожидалась '(' перед параметрами");
            List<Token> params = new ArrayList<>();
            if (!check(Tok.RIGHT_PAREN)) {
                do {
                    params.add(consume(Tok.IDENTIFIER, "Ожидалось имя параметра"));
                } while (match(Tok.COMMA));
            }
            consume(Tok.RIGHT_PAREN, "Ожидалась ')' после параметров");
            return params;
        }

        /** functionDepth нужен, чтобы запретить return на верхнем уровне. */
        private List<Stmt> functionBody() {
            functionDepth++;
            try {
                return block();
            } finally {
                functionDepth--;
            }
        }

        private Stmt varDeclaration() {
            Token name = consume(Tok.IDENTIFIER, "Ожидалось имя переменной");
            Expr initializer = match(Tok.EQUAL) ? expression() : null;
            consume(Tok.SEMICOLON, "Ожидалась ';' после объявления переменной");
            return new VarStmt(name, initializer);
        }

        private Stmt statement() {
            if (match(Tok.FOR)) return forStatement();
            if (match(Tok.IF)) return ifStatement();
            if (match(Tok.WHILE)) return whileStatement();
            if (match(Tok.LEFT_BRACE)) return new BlockStmt(block());
            if (match(Tok.PRINT)) {
                Expr value = expression();
                consume(Tok.SEMICOLON, "Ожидалась ';' после значения");
                return new PrintStmt(value);
            }
            if (match(Tok.RETURN)) {
                if (functionDepth == 0) throw error(previous(), "'return' вне функции");
                Expr value = check(Tok.SEMICOLON) ? null : expression();
                consume(Tok.SEMICOLON, "Ожидалась ';' после return");
                return new ReturnStmt(value);
            }
            Expr expr = expression();
            consume(Tok.SEMICOLON, "Ожидалась ';' после выражения");
            return new ExprStmt(expr);
        }

        private Stmt forStatement() {
            consume(Tok.LEFT_PAREN, "Ожидалась '(' после 'for'");
            Stmt init = null;
            if (match(Tok.VAR)) {
                init = varDeclaration();
            } else if (!match(Tok.SEMICOLON)) {
                init = new ExprStmt(expression());
                consume(Tok.SEMICOLON, "Ожидалась ';' после инициализации");
            }
            Expr condition = check(Tok.SEMICOLON) ? new Literal(true) : expression();
            consume(Tok.SEMICOLON, "Ожидалась ';' после условия цикла");
            Expr increment = check(Tok.RIGHT_PAREN) ? null : expression();
            consume(Tok.RIGHT_PAREN, "Ожидалась ')' после заголовка for");

            Stmt loop = new WhileStmt(condition, statement(), increment);
            return init == null ? loop : new BlockStmt(Arrays.asList(init, loop));
        }

        private Stmt whileStatement() {
            consume(Tok.LEFT_PAREN, "Ожидалась '(' после 'while'");
            Expr condition = expression();
            consume(Tok.RIGHT_PAREN, "Ожидалась ')' после условия");
            return new WhileStmt(condition, statement(), null);
        }

        private Stmt ifStatement() {
            consume(Tok.LEFT_PAREN, "Ожидалась '(' после 'if'");
            Expr condition = expression();
            consume(Tok.RIGHT_PAREN, "Ожидалась ')' после условия");
            Stmt thenBranch = statement();
            Stmt elseBranch = match(Tok.ELSE) ? statement() : null;
            return new IfStmt(condition, thenBranch, elseBranch);
        }

        private List<Stmt> block() {
            List<Stmt> statements = new ArrayList<>();
            while (!check(Tok.RIGHT_BRACE) && !atEnd()) statements.add(declaration());
            consume(Tok.RIGHT_BRACE, "Ожидалась '}' после блока");
            return statements;
        }

        private Expr expression() {
            return assignment();
        }

        /** Составное присваивание раскрывается: a += b  =>  a = a + b. */
        private Expr assignment() {
            Expr expr = binaryLevel(0);
            if (!match(Tok.EQUAL, Tok.PLUS_EQUAL, Tok.MINUS_EQUAL, Tok.STAR_EQUAL, Tok.SLASH_EQUAL)) return expr;
            Token op = previous();
            Expr value = assignment();
            if (op.type != Tok.EQUAL) {
                Tok binary = op.type == Tok.PLUS_EQUAL ? Tok.PLUS
                        : op.type == Tok.MINUS_EQUAL ? Tok.MINUS
                        : op.type == Tok.STAR_EQUAL ? Tok.STAR : Tok.SLASH;
                value = new Binary(expr, new Token(binary, op.lexeme.substring(0, 1), null, op.line), value);
            }
            if (expr instanceof Variable) return new Assign(((Variable) expr).name, value);
            if (expr instanceof Index) {
                Index ix = (Index) expr;
                return new IndexSet(ix.object, ix.bracket, ix.index, value);
            }
            throw error(op, "Недопустимая цель присваивания");
        }

        private Expr binaryLevel(int level) {
            if (level == LEVELS.length) return unary();
            Expr expr = binaryLevel(level + 1);
            while (match(LEVELS[level])) {
                Token op = previous();
                Expr right = binaryLevel(level + 1);
                boolean logical = op.type == Tok.OR || op.type == Tok.AND;
                expr = logical ? new Logical(expr, op, right) : new Binary(expr, op, right);
            }
            return expr;
        }

        private Expr unary() {
            if (match(Tok.BANG, Tok.MINUS)) {
                Token op = previous();
                return new Unary(op, unary());
            }
            return call();
        }

        private Expr call() {
            Expr expr = primary();
            while (true) {
                if (match(Tok.LEFT_PAREN)) {
                    List<Expr> args = new ArrayList<>();
                    if (!check(Tok.RIGHT_PAREN)) {
                        do {
                            args.add(expression());
                        } while (match(Tok.COMMA));
                    }
                    Token paren = consume(Tok.RIGHT_PAREN, "Ожидалась ')' после аргументов");
                    expr = new Call(expr, paren, args);
                } else if (match(Tok.LEFT_BRACKET)) {
                    Token bracket = previous();
                    Expr index = expression();
                    consume(Tok.RIGHT_BRACKET, "Ожидалась ']' после индекса");
                    expr = new Index(expr, bracket, index);
                } else {
                    return expr;
                }
            }
        }

        private Expr primary() {
            if (match(Tok.FALSE)) return new Literal(false);
            if (match(Tok.TRUE)) return new Literal(true);
            if (match(Tok.NIL)) return new Literal(null);
            if (match(Tok.NUMBER, Tok.STRING)) return new Literal(previous().literal);
            if (match(Tok.IDENTIFIER)) return new Variable(previous());
            if (match(Tok.FUN)) {
                List<Token> params = parameters();
                consume(Tok.LEFT_BRACE, "Ожидалась '{' перед телом лямбды");
                return new Lambda(params, functionBody());
            }
            if (match(Tok.LEFT_PAREN)) {
                Expr expr = expression();
                consume(Tok.RIGHT_PAREN, "Ожидалась ')' после выражения");
                return expr;
            }
            if (match(Tok.LEFT_BRACKET)) {
                List<Expr> items = new ArrayList<>();
                while (!check(Tok.RIGHT_BRACKET)) {
                    items.add(expression());
                    if (!match(Tok.COMMA)) break;
                }
                consume(Tok.RIGHT_BRACKET, "Ожидалась ']' после элементов списка");
                return new ListLit(items);
            }
            throw error(peek(), "Ожидалось выражение");
        }

        private boolean match(Tok... types) {
            for (Tok t : types) {
                if (check(t)) {
                    advance();
                    return true;
                }
            }
            return false;
        }

        private boolean check(Tok type) { return !atEnd() && peek().type == type; }
        private boolean atEnd() { return peek().type == Tok.EOF; }
        private Token peek() { return tokens.get(current); }
        private Token previous() { return tokens.get(current - 1); }

        private boolean checkNext(Tok type) {
            return current + 1 < tokens.size() && tokens.get(current + 1).type == type;
        }

        private Token advance() {
            if (!atEnd()) current++;
            return previous();
        }

        private Token consume(Tok type, String message) {
            if (check(type)) return advance();
            throw error(peek(), message);
        }

        private SyntaxError error(Token token, String message) {
            String where = token.type == Tok.EOF ? "в конце файла" : "у '" + token.lexeme + "'";
            return new SyntaxError(token.line, where + ": " + message);
        }
    }

    // ---------- Окружение и функции времени выполнения ----------

    static final class Environment {
        final Environment enclosing;
        private final Map<String, Object> values = new HashMap<>();

        Environment(Environment enclosing) { this.enclosing = enclosing; }

        void define(String name, Object value) { values.put(name, value); }
        Object get(Token name) { return find(name).values.get(name.lexeme); }
        void assign(Token name, Object value) { find(name).values.put(name.lexeme, value); }

        private Environment find(Token name) {
            for (Environment e = this; e != null; e = e.enclosing) {
                if (e.values.containsKey(name.lexeme)) return e;
            }
            throw new RuntimeError(name, "Неопределённая переменная '" + name.lexeme + "'");
        }
    }

    interface Callable {
        /** -1 означает переменное число аргументов. */
        int arity();

        Object call(Interpreter interp, List<Object> args, Token paren);
    }

    interface NativeBody {
        Object apply(Interpreter interp, List<Object> args, Token paren);
    }

    static final class NativeFunction implements Callable {
        private final String name; private final int arity; private final NativeBody body;

        NativeFunction(String name, int arity, NativeBody body) {
            this.name = name; this.arity = arity; this.body = body;
        }

        public int arity() { return arity; }
        public Object call(Interpreter interp, List<Object> args, Token paren) { return body.apply(interp, args, paren); }
        @Override public String toString() { return "<native fn " + name + ">"; }
    }

    static final class MiniFunction implements Callable {
        private final String name; private final List<Token> params;
        private final List<Stmt> body; private final Environment closure;

        MiniFunction(String name, List<Token> params, List<Stmt> body, Environment closure) {
            this.name = name; this.params = params; this.body = body; this.closure = closure;
        }

        public int arity() { return params.size(); }

        public Object call(Interpreter interp, List<Object> args, Token paren) {
            Environment env = new Environment(closure);
            for (int i = 0; i < params.size(); i++) {
                env.define(params.get(i).lexeme, args.get(i));
            }
            try {
                interp.executeBlock(body, env);
            } catch (ReturnSignal r) {
                return r.value;
            }
            return null;
        }

        @Override public String toString() { return "<fn " + name + ">"; }
    }

    // ---------- Общие вспомогательные функции ----------

    static String typeName(Object v) {
        if (v == null) return "nil";
        if (v instanceof Boolean) return "bool";
        if (v instanceof Double) return "number";
        if (v instanceof String) return "string";
        if (v instanceof List) return "list";
        if (v instanceof Callable) return "function";
        return "unknown";
    }

    static String formatNumber(double d) {
        if (Double.isNaN(d)) return "nan";
        if (Double.isInfinite(d)) return d > 0 ? "inf" : "-inf";
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return Long.toString((long) d);
        return Double.toString(d);
    }

    static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    // ---------- Интерпретатор ----------

    @SuppressWarnings("unchecked")
    static final class Interpreter {
        final Environment globals = new Environment(null);
        private Environment env = globals;

        Interpreter() { Builtins.install(this); }

        void interpret(List<Stmt> statements) { for (Stmt s : statements) execute(s); }

        void executeBlock(List<Stmt> statements, Environment blockEnv) {
            Environment previous = env;
            env = blockEnv;
            try {
                for (Stmt s : statements) execute(s);
            } finally {
                env = previous;
            }
        }

        Object callValue(Object callee, List<Object> args, Token paren) {
            if (!(callee instanceof Callable)) {
                throw new RuntimeError(paren, "Вызывать можно только функции, а не " + typeName(callee));
            }
            Callable fn = (Callable) callee;
            if (fn.arity() >= 0 && args.size() != fn.arity()) {
                throw new RuntimeError(paren, "Ожидалось аргументов: " + fn.arity() + ", получено: " + args.size());
            }
            return fn.call(this, args, paren);
        }

        // ---------- инструкции ----------

        private void execute(Stmt s) {
            if (s instanceof ExprStmt) {
                evaluate(((ExprStmt) s).expression);
            } else if (s instanceof PrintStmt) {
                System.out.println(stringify(evaluate(((PrintStmt) s).expression)));
            } else if (s instanceof VarStmt) {
                VarStmt v = (VarStmt) s;
                env.define(v.name.lexeme, v.initializer == null ? null : evaluate(v.initializer));
            } else if (s instanceof BlockStmt) {
                executeBlock(((BlockStmt) s).statements, new Environment(env));
            } else if (s instanceof IfStmt) {
                IfStmt i = (IfStmt) s;
                if (isTruthy(evaluate(i.condition))) execute(i.thenBranch);
                else if (i.elseBranch != null) execute(i.elseBranch);
            } else if (s instanceof WhileStmt) {
                WhileStmt w = (WhileStmt) s;
                while (isTruthy(evaluate(w.condition))) {
                    execute(w.body);
                    if (w.increment != null) evaluate(w.increment);
                }
            } else if (s instanceof FunctionStmt) {
                FunctionStmt f = (FunctionStmt) s;
                env.define(f.name.lexeme, new MiniFunction(f.name.lexeme, f.params, f.body, env));
            } else if (s instanceof ReturnStmt) {
                Expr value = ((ReturnStmt) s).value;
                throw new ReturnSignal(value == null ? null : evaluate(value));
            } else {
                throw new IllegalStateException("Неизвестная инструкция: " + s.getClass().getSimpleName());
            }
        }

        // ---------- выражения ----------

        private Object evaluate(Expr e) {
            if (e instanceof Literal) return ((Literal) e).value;
            if (e instanceof Variable) return env.get(((Variable) e).name);
            if (e instanceof Assign) {
                Assign a = (Assign) e;
                Object value = evaluate(a.value);
                env.assign(a.name, value);
                return value;
            }
            if (e instanceof Binary) return evalBinary((Binary) e);
            if (e instanceof Logical) {
                Logical l = (Logical) e;
                Object left = evaluate(l.left);
                if (l.op.type == Tok.OR ? isTruthy(left) : !isTruthy(left)) return left;
                return evaluate(l.right);
            }
            if (e instanceof Unary) {
                Unary u = (Unary) e;
                Object right = evaluate(u.right);
                return u.op.type == Tok.BANG ? (Object) !isTruthy(right) : (Object) (-num(u.op, right));
            }
            if (e instanceof Call) {
                Call c = (Call) e;
                Object callee = evaluate(c.callee);
                List<Object> args = new ArrayList<>();
                for (Expr a : c.args) args.add(evaluate(a));
                return callValue(callee, args, c.paren);
            }
            if (e instanceof Index) return evalIndex((Index) e);
            if (e instanceof IndexSet) return evalIndexSet((IndexSet) e);
            if (e instanceof ListLit) {
                List<Object> list = new ArrayList<>();
                for (Expr item : ((ListLit) e).items) list.add(evaluate(item));
                return list;
            }
            if (e instanceof Lambda) {
                Lambda l = (Lambda) e;
                return new MiniFunction("lambda", l.params, l.body, env);
            }
            throw new IllegalStateException("Неизвестное выражение: " + e.getClass().getSimpleName());
        }

        private Object evalBinary(Binary e) {
            Object l = evaluate(e.left);
            Object r = evaluate(e.right);
            switch (e.op.type) {
                case PLUS:
                    if (l instanceof Double && r instanceof Double) return (Double) l + (Double) r;
                    if (l instanceof String || r instanceof String) return stringify(l) + stringify(r);
                    if (l instanceof List && r instanceof List) {
                        List<Object> joined = new ArrayList<>((List<Object>) l);
                        joined.addAll((List<Object>) r);
                        return joined;
                    }
                    throw new RuntimeError(e.op, "'+' применим к числам, строкам и спискам");
                case STAR:
                    if (l instanceof String && r instanceof Double) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < (Double) r; i++) sb.append((String) l);
                        return sb.toString();
                    }
                    return num(e.op, l) * num(e.op, r);
                case MINUS: return num(e.op, l) - num(e.op, r);
                case SLASH: return num(e.op, l) / nonZero(e.op, num(e.op, r));
                case PERCENT: return num(e.op, l) % nonZero(e.op, num(e.op, r));
                case GREATER: return compareValues(e.op, l, r) > 0;
                case GREATER_EQUAL: return compareValues(e.op, l, r) >= 0;
                case LESS: return compareValues(e.op, l, r) < 0;
                case LESS_EQUAL: return compareValues(e.op, l, r) <= 0;
                case EQUAL_EQUAL: return Objects.equals(l, r);
                case BANG_EQUAL: return !Objects.equals(l, r);
                default: throw new RuntimeError(e.op, "Неизвестный бинарный оператор");
            }
        }

        private Object evalIndex(Index e) {
            Object obj = evaluate(e.object);
            Object idx = evaluate(e.index);
            if (obj instanceof List) {
                List<Object> list = (List<Object>) obj;
                return list.get(toIndex(e.bracket, idx, list.size()));
            }
            if (obj instanceof String) {
                String s = (String) obj;
                return String.valueOf(s.charAt(toIndex(e.bracket, idx, s.length())));
            }
            throw new RuntimeError(e.bracket, "Индексировать можно только списки и строки");
        }

        private Object evalIndexSet(IndexSet e) {
            Object obj = evaluate(e.object);
            Object idx = evaluate(e.index);
            Object value = evaluate(e.value);
            if (!(obj instanceof List)) throw new RuntimeError(e.bracket, "Присваивать по индексу можно только в списки");
            List<Object> list = (List<Object>) obj;
            list.set(toIndex(e.bracket, idx, list.size()), value);
            return value;
        }

        // ---------- вспомогательное ----------

        boolean isTruthy(Object v) {
            if (v == null) return false;
            if (v instanceof Boolean) return (Boolean) v;
            return true;
        }

        int compareValues(Token op, Object l, Object r) {
            if (l instanceof Double && r instanceof Double) return Double.compare((Double) l, (Double) r);
            if (l instanceof String && r instanceof String) return ((String) l).compareTo((String) r);
            throw new RuntimeError(op, "Нельзя сравнить " + typeName(l) + " и " + typeName(r));
        }

        private double num(Token op, Object v) {
            if (v instanceof Double) return (Double) v;
            throw new RuntimeError(op, "Оператор '" + op.lexeme + "' требует число, получено " + typeName(v));
        }

        private double nonZero(Token op, double d) {
            if (d == 0) throw new RuntimeError(op, "Деление на ноль");
            return d;
        }

        private int toIndex(Token at, Object idx, int size) {
            if (!(idx instanceof Double) || (Double) idx != Math.floor((Double) idx)) {
                throw new RuntimeError(at, "Индекс должен быть целым числом");
            }
            int i = ((Double) idx).intValue();
            if (i < 0) i += size;
            if (i < 0 || i >= size) throw new RuntimeError(at, "Индекс " + formatNumber((Double) idx) + " вне диапазона");
            return i;
        }

        String stringify(Object v) {
            return show(v, false);
        }

        private String show(Object v, boolean quoted) {
            if (v == null) return "nil";
            if (v instanceof Double) return formatNumber((Double) v);
            if (v instanceof String) return quoted ? quote((String) v) : (String) v;
            if (v instanceof List) {
                StringBuilder sb = new StringBuilder("[");
                for (Object item : (List<Object>) v) {
                    if (sb.length() > 1) sb.append(", ");
                    sb.append(show(item, true));
                }
                return sb.append(']').toString();
            }
            return v.toString();
        }
    }

    // ---------- Встроенные функции ----------

    @SuppressWarnings("unchecked")
    static final class Builtins {
        private Builtins() { }

        static void install(Interpreter in) {
            // --- общие ---
            def(in, "clock", 0, (it, a, p) -> System.nanoTime() / 1e9);
            def(in, "type", 1, (it, a, p) -> typeName(a.get(0)));
            def(in, "str", 1, (it, a, p) -> it.stringify(a.get(0)));
            def(in, "len", 1, (it, a, p) -> {
                Object v = a.get(0);
                if (v instanceof String) return (double) ((String) v).length();
                if (v instanceof List) return (double) ((List<Object>) v).size();
                throw new RuntimeError(p, "len() ожидает строку или список");
            });

            // --- списки ---
            def(in, "push", 2, (it, a, p) -> {
                List<Object> list = argList(a, 0, p, "push");
                list.add(a.get(1));
                return list;
            });
            def(in, "pop", 1, (it, a, p) -> {
                List<Object> list = argList(a, 0, p, "pop");
                if (list.isEmpty()) throw new RuntimeError(p, "pop() из пустого списка");
                return list.remove(list.size() - 1);
            });
            def(in, "range", -1, (it, a, p) -> {
                if (a.isEmpty() || a.size() > 3) throw new RuntimeError(p, "range() принимает от 1 до 3 аргументов");
                double from = a.size() == 1 ? 0 : argNum(a, 0, p, "range");
                double to = argNum(a, a.size() == 1 ? 0 : 1, p, "range");
                double step = a.size() == 3 ? argNum(a, 2, p, "range") : 1;
                if (step == 0) throw new RuntimeError(p, "range(): шаг не может быть нулём");
                List<Object> out = new ArrayList<>();
                for (double x = from; step > 0 ? x < to : x > to; x += step) out.add(x);
                return out;
            });
            def(in, "sort", 1, (it, a, p) -> {
                List<Object> copy = new ArrayList<>(argList(a, 0, p, "sort"));
                copy.sort((x, y) -> it.compareValues(p, x, y));
                return copy;
            });

            // --- функции высшего порядка ---
            def(in, "map", 2, (it, a, p) -> {
                Callable f = argFn(a, 1, p, "map");
                List<Object> out = new ArrayList<>();
                for (Object x : new ArrayList<>(argList(a, 0, p, "map"))) {
                    out.add(it.callValue(f, Collections.singletonList(x), p));
                }
                return out;
            });
            def(in, "filter", 2, (it, a, p) -> {
                Callable f = argFn(a, 1, p, "filter");
                List<Object> out = new ArrayList<>();
                for (Object x : new ArrayList<>(argList(a, 0, p, "filter"))) {
                    if (it.isTruthy(it.callValue(f, Collections.singletonList(x), p))) out.add(x);
                }
                return out;
            });
            def(in, "reduce", 3, (it, a, p) -> {
                Callable f = argFn(a, 1, p, "reduce");
                Object acc = a.get(2);
                for (Object x : new ArrayList<>(argList(a, 0, p, "reduce"))) {
                    acc = it.callValue(f, Arrays.asList(acc, x), p);
                }
                return acc;
            });

            // --- математика ---
            def(in, "abs", 1, (it, a, p) -> Math.abs(argNum(a, 0, p, "abs")));
            def(in, "sqrt", 1, (it, a, p) -> Math.sqrt(argNum(a, 0, p, "sqrt")));

            // --- строки ---
            def(in, "upper", 1, (it, a, p) -> argStr(a, 0, p, "upper").toUpperCase());
            def(in, "lower", 1, (it, a, p) -> argStr(a, 0, p, "lower").toLowerCase());
            def(in, "split", 2, (it, a, p) -> {
                String s = argStr(a, 0, p, "split");
                String sep = argStr(a, 1, p, "split");
                List<Object> out = new ArrayList<>();
                int from = 0;
                int idx;
                while (!sep.isEmpty() && (idx = s.indexOf(sep, from)) >= 0) {
                    out.add(s.substring(from, idx));
                    from = idx + sep.length();
                }
                out.add(s.substring(from));
                return out;
            });
            def(in, "join", 2, (it, a, p) -> {
                List<Object> list = argList(a, 0, p, "join");
                String sep = argStr(a, 1, p, "join");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(sep);
                    sb.append(it.stringify(list.get(i)));
                }
                return sb.toString();
            });
        }

        private static void def(Interpreter in, String name, int arity, NativeBody body) {
            in.globals.define(name, new NativeFunction(name, arity, body));
        }

        private static RuntimeError badArg(Token p, String fn, int i, String expected, Object got) {
            return new RuntimeError(p, fn + "(): аргумент " + (i + 1) + " должен быть " + expected
                    + ", получено " + typeName(got));
        }

        private static double argNum(List<Object> a, int i, Token p, String fn) {
            Object v = a.get(i);
            if (!(v instanceof Double)) throw badArg(p, fn, i, "числом", v);
            return (Double) v;
        }

        private static String argStr(List<Object> a, int i, Token p, String fn) {
            Object v = a.get(i);
            if (!(v instanceof String)) throw badArg(p, fn, i, "строкой", v);
            return (String) v;
        }

        private static List<Object> argList(List<Object> a, int i, Token p, String fn) {
            Object v = a.get(i);
            if (!(v instanceof List)) throw badArg(p, fn, i, "списком", v);
            return (List<Object>) v;
        }

        private static Callable argFn(List<Object> a, int i, Token p, String fn) {
            Object v = a.get(i);
            if (!(v instanceof Callable)) throw badArg(p, fn, i, "функцией", v);
            return (Callable) v;
        }
    }

    // ---------- Демонстрационная программа ----------

    static final String DEMO = String.join("\n",
            "print \"=== MiniLang demo ===\";",
            "",
            "// Рекурсия и списки",
            "fun fib(n) {",
            "  if (n < 2) return n;",
            "  return fib(n - 1) + fib(n - 2);",
            "}",
            "var fibs = [];",
            "for (var i = 0; i < 15; i += 1) push(fibs, fib(i));",
            "print \"fib: \" + fibs;",
            "print \"последний: \" + fibs[-1] + \", всего: \" + len(fibs);",
            "",
            "// Замыкания",
            "fun makeCounter() {",
            "  var count = 0;",
            "  return fun() { count += 1; return count; };",
            "}",
            "var counter = makeCounter();",
            "counter(); counter();",
            "print \"счётчик: \" + counter();",
            "",
            "// Функции высшего порядка",
            "var squares = map(range(1, 11), fun(x) { return x * x; });",
            "var evens = filter(squares, fun(x) { return x % 2 == 0; });",
            "var total = reduce(evens, fun(acc, x) { return acc + x; }, 0);",
            "print \"квадраты: \" + squares;",
            "print \"чётные: \" + evens + \", сумма = \" + total;",
            "",
            "// Сортировка пузырьком",
            "fun bubble(xs) {",
            "  var a = xs + [];",
            "  var n = len(a);",
            "  var swapped = true;",
            "  while (swapped) {",
            "    swapped = false;",
            "    for (var i = 1; i < n; i += 1) {",
            "      if (a[i - 1] > a[i]) {",
            "        var t = a[i - 1]; a[i - 1] = a[i]; a[i] = t;",
            "        swapped = true;",
            "      }",
            "    }",
            "    n -= 1;",
            "  }",
            "  return a;",
            "}",
            "print bubble([5, 3, 9, 1, 4, 8, 2]);",
            "",
            "// Строки",
            "var words = split(\"the quick brown fox jumps over the lazy dog\", \" \");",
            "print join(sort(words), \", \");",
            "print upper(\"mini\") + lower(\"LANG\") + \" \" + \"=\" * 10;",
            "print \"=== готово ===\";");

    // ---------- Точка входа ----------

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.err.println("Использование: java MiniLang [script.mini]");
            System.exit(64);
        }
        String source = args.length == 0
                ? DEMO
                : new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
        int code = run(source, new Interpreter());
        if (code != 0) System.exit(code);
    }

    /** Возвращает код выхода: 0 — успех, 65 — синтаксическая ошибка, 70 — ошибка выполнения. */
    static int run(String source, Interpreter interp) {
        List<Stmt> statements;
        try {
            statements = new Parser(new Lexer(source).scan()).parse();
        } catch (SyntaxError e) {
            System.err.println("[строка " + e.line + "] Синтаксическая ошибка " + e.getMessage());
            return 65;
        }
        try {
            interp.interpret(statements);
        } catch (RuntimeError e) {
            String where = e.token != null ? "[строка " + e.token.line + "] " : "";
            System.err.println(where + "Ошибка выполнения: " + e.getMessage());
            return 70;
        } catch (StackOverflowError e) {
            System.err.println("Ошибка выполнения: слишком глубокая рекурсия");
            return 70;
        }
        return 0;
    }
}
