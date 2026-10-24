package blueprints.export;

/** Число со словом в нужной форме: 1 тест, 2 теста, 5 тестов. */
final class Plural {

    private Plural() {}

    static String of(int n, String one, String few, String many) {
        int lastTwo = Math.abs(n) % 100;
        int last = lastTwo % 10;
        String word;
        if (lastTwo >= 11 && lastTwo <= 14) {
            word = many;
        } else if (last == 1) {
            word = one;
        } else if (last >= 2 && last <= 4) {
            word = few;
        } else {
            word = many;
        }
        return n + " " + word;
    }
}
