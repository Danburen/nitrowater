package demo;

import java.util.List;

/**
 * A tiny calculator used as the AST-analysis target for the AutoGenTestCase demo.
 * Every method deliberately carries edge cases (divide-by-zero, negative input,
 * null / empty input) so that the generated JUnit tests have something to cover.
 */
public class Calculator {

    /** Adds two integers and returns the sum. */
    public int add(int a, int b) {
        return a + b;
    }

    /** Integer division. Throws {@link ArithmeticException} when {@code b == 0}. */
    public int divide(int a, int b) {
        if (b == 0) {
            throw new ArithmeticException("division by zero");
        }
        return a / b;
    }

    /** Factorial of {@code n}. Throws {@link IllegalArgumentException} when {@code n < 0}. */
    public long factorial(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be >= 0");
        }
        long result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }

    /**
     * Returns {@code true} when the text is a palindrome, ignoring case and whitespace;
     * returns {@code false} for {@code null} or empty input.
     */
    public boolean isPalindrome(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        String cleaned = s.replaceAll("\\s+", "").toLowerCase();
        int i = 0;
        int j = cleaned.length() - 1;
        while (i < j) {
            if (cleaned.charAt(i) != cleaned.charAt(j)) {
                return false;
            }
            i++;
            j--;
        }
        return true;
    }

    /** Sums the values; returns {@code 0} for {@code null} or an empty list. */
    public int sum(List<Integer> values) {
        if (values == null) {
            return 0;
        }
        int total = 0;
        for (Integer v : values) {
            total += v;
        }
        return total;
    }

    public static void main(String[] args) {
        System.out.println(new Calculator().add(1, 2));
    }
}
