import java.io.*;
public class ReaderLoop {
    public static void main(String[] args) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        int sum = 0;
        String line;
        while (!"exit".equals(line = r.readLine())) {
            if (line == null) continue;
            for (String t : line.trim().split("\s+")) if (!t.isEmpty()) sum += Integer.parseInt(t);
            System.out.println("sum " + sum);
        }
    }
}
