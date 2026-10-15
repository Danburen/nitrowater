/*
 * Self-contained C harness for LeetCode-style function problems.
 *
 * The generator (CHarness / CppHarness) substitutes the USER_CODE placeholder with the user's own
 * function(s), the CASE_BODY placeholder with the per-signature parameter bindings/call/serialisation,
 * the LANG_INCLUDES placeholder with the language header, the TYPE_DEFS placeholder with any
 * node type definitions the user's own body needs (placed before USER_CODE, since a user-defined node
 * struct would otherwise not be declared in time) and the HELPERS placeholder with any
 * language-specific helper functions (placed after USER_CODE, as only the driver calls them). (The
 * placeholders are spelled without braces here so this comment is left intact.)
 *
 * JSON is parsed by a tiny hand-written reader bundled below, so the submission needs no external
 * library. The program reads a JSON array of case objects from stdin and prints one JSON result per
 * case (one line each), matching how the judge compares outputs.
 */

{{LANG_INCLUDES}}

/* type: 0 null, 1 bool, 2 number, 3 string, 4 array, 5 object */
typedef struct Nv {
    int type;
    double num;
    char *str;
    struct Nv **items;
    char **keys;
    int len;
} Nv;

static char *__nt_src;
static int __nt_pos;

static Nv *__nt_new(int type) {
    Nv *v = (Nv *) calloc(1, sizeof(Nv));
    v->type = type;
    return v;
}

static void __nt_ws(void) {
    while (__nt_src[__nt_pos] == ' ' || __nt_src[__nt_pos] == '\t'
        || __nt_src[__nt_pos] == '\n' || __nt_src[__nt_pos] == '\r') {
        __nt_pos++;
    }
}

static char *__nt_string(void) {
    __nt_pos++; /* opening quote */
    size_t cap = 16, len = 0;
    char *out = (char *) malloc(cap);
    while (__nt_src[__nt_pos] != '\0' && __nt_src[__nt_pos] != '"') {
        char c = __nt_src[__nt_pos++];
        if (c == '\\') {
            char e = __nt_src[__nt_pos++];
            switch (e) {
                case 'n': c = '\n'; break;
                case 't': c = '\t'; break;
                case 'r': c = '\r'; break;
                case 'b': c = '\b'; break;
                case 'f': c = '\f'; break;
                case '/': c = '/'; break;
                case '"': c = '"'; break;
                case '\\': c = '\\'; break;
                case 'u': {
                    int cp = 0, k;
                    for (k = 0; k < 4; k++) {
                        char h = __nt_src[__nt_pos++];
                        cp <<= 4;
                        if (h >= '0' && h <= '9') cp |= h - '0';
                        else if (h >= 'a' && h <= 'f') cp |= h - 'a' + 10;
                        else if (h >= 'A' && h <= 'F') cp |= h - 'A' + 10;
                    }
                    if (len + 4 >= cap) { cap = cap * 2 + 4; out = (char *) realloc(out, cap); }
                    if (cp < 0x80) {
                        out[len++] = (char) cp;
                    } else if (cp < 0x800) {
                        out[len++] = (char) (0xC0 | (cp >> 6));
                        out[len++] = (char) (0x80 | (cp & 0x3F));
                    } else {
                        out[len++] = (char) (0xE0 | (cp >> 12));
                        out[len++] = (char) (0x80 | ((cp >> 6) & 0x3F));
                        out[len++] = (char) (0x80 | (cp & 0x3F));
                    }
                    continue;
                }
                default: c = e;
            }
        }
        if (len + 1 >= cap) { cap = cap * 2 + 4; out = (char *) realloc(out, cap); }
        out[len++] = c;
    }
    if (__nt_src[__nt_pos] == '"') __nt_pos++;
    out[len] = '\0';
    return out;
}

static Nv *__nt_val(void) {
    __nt_ws();
    char c = __nt_src[__nt_pos];
    if (c == '{') {
        __nt_pos++;
        Nv *v = __nt_new(5);
        int cap = 8;
        v->keys = (char **) malloc(sizeof(char *) * (size_t) cap);
        v->items = (Nv **) malloc(sizeof(Nv *) * (size_t) cap);
        __nt_ws();
        if (__nt_src[__nt_pos] == '}') { __nt_pos++; return v; }
        while (1) {
            __nt_ws();
            char *k = __nt_string();
            __nt_ws();
            if (__nt_src[__nt_pos] == ':') __nt_pos++;
            Nv *val = __nt_val();
            if (v->len == cap) {
                cap *= 2;
                v->keys = (char **) realloc(v->keys, sizeof(char *) * (size_t) cap);
                v->items = (Nv **) realloc(v->items, sizeof(Nv *) * (size_t) cap);
            }
            v->keys[v->len] = k;
            v->items[v->len] = val;
            v->len++;
            __nt_ws();
            if (__nt_src[__nt_pos] == ',') { __nt_pos++; continue; }
            break;
        }
        if (__nt_src[__nt_pos] == '}') __nt_pos++;
        return v;
    }
    if (c == '[') {
        __nt_pos++;
        Nv *v = __nt_new(4);
        int cap = 8;
        v->items = (Nv **) malloc(sizeof(Nv *) * (size_t) cap);
        __nt_ws();
        if (__nt_src[__nt_pos] == ']') { __nt_pos++; return v; }
        while (1) {
            v->items[v->len] = __nt_val();
            v->len++;
            if (v->len == cap) {
                cap *= 2;
                v->items = (Nv **) realloc(v->items, sizeof(Nv *) * (size_t) cap);
            }
            __nt_ws();
            if (__nt_src[__nt_pos] == ',') { __nt_pos++; continue; }
            break;
        }
        if (__nt_src[__nt_pos] == ']') __nt_pos++;
        return v;
    }
    if (c == '"') {
        Nv *v = __nt_new(3);
        v->str = __nt_string();
        return v;
    }
    if (!strncmp(__nt_src + __nt_pos, "true", 4)) { __nt_pos += 4; Nv *v = __nt_new(1); v->num = 1; return v; }
    if (!strncmp(__nt_src + __nt_pos, "false", 5)) { __nt_pos += 5; Nv *v = __nt_new(1); v->num = 0; return v; }
    if (!strncmp(__nt_src + __nt_pos, "null", 4)) { __nt_pos += 4; return __nt_new(0); }
    {
        char *end;
        double d = strtod(__nt_src + __nt_pos, &end);
        if (end != __nt_src + __nt_pos) {
            Nv *v = __nt_new(2);
            v->num = d;
            __nt_pos = (int) (end - __nt_src);
            return v;
        }
    }
    return __nt_new(0);
}

static Nv *__nt_get(Nv *o, const char *k) {
    if (!o || o->type != 5) return NULL;
    for (int i = 0; i < o->len; i++) {
        if (strcmp(o->keys[i], k) == 0) return o->items[i];
    }
    return NULL;
}

static int __nt_int(Nv *o, const char *k) {
    Nv *v = __nt_get(o, k);
    if (v && (v->type == 2 || v->type == 1)) return (int) v->num;
    return 0;
}

static long __nt_long(Nv *o, const char *k) {
    Nv *v = __nt_get(o, k);
    return (v && v->type == 2) ? (long) v->num : 0L;
}

static char *__nt_str(Nv *o, const char *k) {
    Nv *v = __nt_get(o, k);
    return (v && v->type == 3) ? v->str : (char *) "";
}

static int *__nt_intarr(Nv *o, const char *k, int *n) {
    Nv *v = __nt_get(o, k);
    if (!v || v->type != 4) { *n = 0; return NULL; }
    int *arr = (int *) malloc(sizeof(int) * (size_t) (v->len > 0 ? v->len : 1));
    for (int i = 0; i < v->len; i++) {
        arr[i] = (v->items[i] && v->items[i]->type == 2) ? (int) v->items[i]->num : 0;
    }
    *n = v->len;
    return arr;
}

static void __nt_put_str(const char *s) {
    putchar('"');
    for (const unsigned char *p = (const unsigned char *) s; *p; p++) {
        switch (*p) {
            case '"': fputs("\\\"", stdout); break;
            case '\\': fputs("\\\\", stdout); break;
            case '\n': fputs("\\n", stdout); break;
            case '\r': fputs("\\r", stdout); break;
            case '\t': fputs("\\t", stdout); break;
            default:
                if (*p < 0x20) printf("\\u%04x", *p);
                else putchar(*p);
        }
    }
    putchar('"');
}

static void __nt_put_intarr(const int *a, int n) {
    putchar('[');
    for (int i = 0; i < n; i++) {
        if (i) putchar(',');
        printf("%d", a[i]);
    }
    putchar(']');
}

{{TYPE_DEFS}}

#line 1
{{USER_CODE}}

{{HELPERS}}

int main(void) {
    size_t __nt_cap = 65536, __nt_len = 0;
    char *__nt_buf = (char *) malloc(__nt_cap);
    size_t __nt_read;
    while ((__nt_read = fread(__nt_buf + __nt_len, 1, __nt_cap - __nt_len, stdin)) > 0) {
        __nt_len += __nt_read;
        if (__nt_len == __nt_cap) {
            __nt_cap *= 2;
            __nt_buf = (char *) realloc(__nt_buf, __nt_cap);
        }
    }
    __nt_buf[__nt_len] = '\0';
    __nt_src = __nt_buf;
    __nt_pos = 0;
    Nv *__root = __nt_val();
    if (!__root || __root->type != 4) return 0;
    for (int __i = 0; __i < __root->len; __i++) {
        Nv *__c = __root->items[__i];
{{CASE_BODY}}
        putchar('\n');
    }
    return 0;
}
