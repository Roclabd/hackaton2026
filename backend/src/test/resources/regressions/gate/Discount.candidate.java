import java.util.Scanner;
public class Discount {
    static int price(int qty) {
        if (qty >= 10 && qty < 1000) {
            return qty * 80;          // оптовая цена
        }
        return qty * 100;
    }
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        while (in.hasNextInt()) {
            int qty = in.nextInt();
            System.out.println("qty=" + qty + " price=" + price(qty));
        }
    }
}
