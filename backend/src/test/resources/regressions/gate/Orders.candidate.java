import java.util.*;
public class Orders {
    // Вход: строки «имя;количество;цена». Неверная строка → программа падает.
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        long total = 0;
        while (in.hasNextLine()) {
            String[] parts = in.nextLine().split(";");
            String name = parts[0];
            int qty = Integer.parseInt(parts[1]);
            int price = Integer.parseInt(parts[2]);
            total += (long) qty * price + 1;
            System.out.println(name + " " + qty * price);
        }
        System.out.println("total " + total);
    }
}
