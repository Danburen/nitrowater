import java.util.*;
import java.util.stream.*;
import java.util.function.*;
import java.math.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
{{USER_IMPORTS}}
public class Main {
    public static void main(String[] args) throws Exception {
        String in = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        List<Object> cases = (List<Object>) H.parse(in);
        StringBuilder sb = new StringBuilder();
        for (Object __c : cases) {
            Map<String, Object> __m = (Map<String, Object>) __c;
{{ASSIGNMENTS}}
            Object __r = new Solution().{{METHOD}}({{ARGS}});
            sb.append({{RETURN_SERIALIZE}}).append('\n');
        }
        System.out.print(sb);
    }

    static final class H {
        int p;
        String s;

        static Object parse(String t) {
            H h = new H();
            h.s = t;
            return h.val();
        }

        void ws() {
            while (p < s.length() && Character.isWhitespace(s.charAt(p))) p++;
        }

        Object val() {
            ws();
            char c = s.charAt(p);
            if (c == '{') {
                p++;
                Map<String, Object> m = new LinkedHashMap<>();
                ws();
                if (s.charAt(p) == '}') { p++; return m; }
                while (true) {
                    String k = str();
                    ws();
                    p++;
                    Object v = val();
                    m.put(k, v);
                    ws();
                    char d = s.charAt(p);
                    p++;
                    if (d == '}') break;
                }
                return m;
            }
            if (c == '[') {
                p++;
                List<Object> l = new ArrayList<>();
                ws();
                if (s.charAt(p) == ']') { p++; return l; }
                while (true) {
                    l.add(val());
                    ws();
                    char d = s.charAt(p);
                    p++;
                    if (d == ']') break;
                }
                return l;
            }
            if (c == '"') return str();
            int st = p;
            while (p < s.length() && "-+.eE0123456789truefalsn".indexOf(s.charAt(p)) >= 0) p++;
            String tok = s.substring(st, p);
            if (tok.equals("true")) return Boolean.TRUE;
            if (tok.equals("false")) return Boolean.FALSE;
            if (tok.equals("null")) return null;
            return Double.valueOf(tok);
        }

        String str() {
            StringBuilder b = new StringBuilder();
            p++;
            while (true) {
                char c = s.charAt(p++);
                if (c == '"') break;
                if (c == '\\') {
                    char e = s.charAt(p++);
                    switch (e) {
                        case 'n': b.append('\n'); break;
                        case 't': b.append('\t'); break;
                        case 'r': b.append('\r'); break;
                        case 'b': b.append('\b'); break;
                        case 'f': b.append('\f'); break;
                        case '"': b.append('"'); break;
                        case '\\': b.append('\\'); break;
                        case '/': b.append('/'); break;
                        case 'u': b.append((char) Integer.parseInt(s.substring(p, p + 4), 16)); p += 4; break;
                        default: b.append(e);
                    }
                } else {
                    b.append(c);
                }
            }
            return b.toString();
        }

        static int i(Object o) { return ((Number) o).intValue(); }
        static long l(Object o) { return ((Number) o).longValue(); }
        static double d(Object o) { return ((Number) o).doubleValue(); }
        static boolean b(Object o) { return (Boolean) o; }
        static char ch(Object o) { return o instanceof Number ? (char) ((Number) o).intValue() : ((String) o).charAt(0); }
        static String str2(Object o) { return o == null ? null : String.valueOf(o); }

        static int[] intArr(Object o) { List<Object> x = (List<Object>) o; int[] r = new int[x.size()]; for (int k = 0; k < r.length; k++) r[k] = ((Number) x.get(k)).intValue(); return r; }
        static long[] longArr(Object o) { List<Object> x = (List<Object>) o; long[] r = new long[x.size()]; for (int k = 0; k < r.length; k++) r[k] = ((Number) x.get(k)).longValue(); return r; }
        static double[] doubleArr(Object o) { List<Object> x = (List<Object>) o; double[] r = new double[x.size()]; for (int k = 0; k < r.length; k++) r[k] = ((Number) x.get(k)).doubleValue(); return r; }
        static boolean[] boolArr(Object o) { List<Object> x = (List<Object>) o; boolean[] r = new boolean[x.size()]; for (int k = 0; k < r.length; k++) r[k] = (Boolean) x.get(k); return r; }
        static char[] charArr(Object o) { List<Object> x = (List<Object>) o; char[] r = new char[x.size()]; for (int k = 0; k < r.length; k++) r[k] = ch(x.get(k)); return r; }
        static String[] strArr(Object o) { List<Object> x = (List<Object>) o; String[] r = new String[x.size()]; for (int k = 0; k < r.length; k++) r[k] = str2(x.get(k)); return r; }
        static int[][] int2D(Object o) { List<Object> x = (List<Object>) o; int[][] r = new int[x.size()][]; for (int k = 0; k < r.length; k++) r[k] = intArr(x.get(k)); return r; }
        static List<Integer> intList(Object o) { List<Object> x = (List<Object>) o; List<Integer> r = new ArrayList<>(); for (Object e : x) r.add(((Number) e).intValue()); return r; }
        static List<Long> longList(Object o) { List<Object> x = (List<Object>) o; List<Long> r = new ArrayList<>(); for (Object e : x) r.add(((Number) e).longValue()); return r; }
        static List<String> strList(Object o) { List<Object> x = (List<Object>) o; List<String> r = new ArrayList<>(); for (Object e : x) r.add(str2(e)); return r; }
        static List<List<Integer>> int2DList(Object o) { List<Object> x = (List<Object>) o; List<List<Integer>> r = new ArrayList<>(); for (Object e : x) r.add(intList(e)); return r; }

{{NODE_HELPERS}}
        static String json(Object o) {
            if (o == null) return "null";
            if (o instanceof String) return "\"" + esc((String) o) + "\"";
            if (o instanceof Character) return "\"" + esc(String.valueOf(o)) + "\"";
            if (o instanceof Boolean || o instanceof Number) return String.valueOf(o);
            if (o instanceof int[]) { int[] x = (int[]) o; StringBuilder b = new StringBuilder("["); for (int k = 0; k < x.length; k++) { if (k > 0) b.append(','); b.append(x[k]); } return b.append(']').toString(); }
            if (o instanceof long[]) { long[] x = (long[]) o; StringBuilder b = new StringBuilder("["); for (int k = 0; k < x.length; k++) { if (k > 0) b.append(','); b.append(x[k]); } return b.append(']').toString(); }
            if (o instanceof double[]) { double[] x = (double[]) o; StringBuilder b = new StringBuilder("["); for (int k = 0; k < x.length; k++) { if (k > 0) b.append(','); b.append(x[k]); } return b.append(']').toString(); }
            if (o instanceof boolean[]) { boolean[] x = (boolean[]) o; StringBuilder b = new StringBuilder("["); for (int k = 0; k < x.length; k++) { if (k > 0) b.append(','); b.append(x[k]); } return b.append(']').toString(); }
            if (o instanceof char[]) { char[] x = (char[]) o; StringBuilder b = new StringBuilder("["); for (int k = 0; k < x.length; k++) { if (k > 0) b.append(','); b.append("\"").append(esc(String.valueOf(x[k]))).append("\""); } return b.append(']').toString(); }
            if (o instanceof Object[]) { Object[] x = (Object[]) o; StringBuilder b = new StringBuilder("["); for (int k = 0; k < x.length; k++) { if (k > 0) b.append(','); b.append(json(x[k])); } return b.append(']').toString(); }
            if (o instanceof Collection) { StringBuilder b = new StringBuilder("["); boolean f = true; for (Object e : (Collection<?>) o) { if (!f) b.append(','); f = false; b.append(json(e)); } return b.append(']').toString(); }
            if (o instanceof Map) { StringBuilder b = new StringBuilder("{"); boolean f = true; for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) { if (!f) b.append(','); f = false; b.append(json(String.valueOf(e.getKey()))).append(':').append(json(e.getValue())); } return b.append('}').toString(); }
            return "\"" + esc(String.valueOf(o)) + "\"";
        }

        static String esc(String v) {
            StringBuilder b = new StringBuilder();
            for (int k = 0; k < v.length(); k++) {
                char c = v.charAt(k);
                switch (c) {
                    case '"': b.append("\\\""); break;
                    case '\\': b.append("\\\\"); break;
                    case '\n': b.append("\\n"); break;
                    case '\r': b.append("\\r"); break;
                    case '\t': b.append("\\t"); break;
                    default: b.append(c);
                }
            }
            return b.toString();
        }
    }
}
