package com.fanli.sakurazakatranslator.translation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Protects symbols already in the input. It never guesses what an OCR engine lost. */
public final class SymbolProtector {
    private SymbolProtector() { }

    public static ProtectedText protect(String original) {
        Objects.requireNonNull(original);
        String prefix = "__S46_";
        while (original.contains(prefix)) prefix += "_";
        StringBuilder text = new StringBuilder();
        List<String> values = new ArrayList<>();
        for (int offset = 0; offset < original.length();) {
            int cp = original.codePointAt(offset);
            int end = offset + Character.charCount(cp);
            if (protectedAt(original, offset)) {
                while (end < original.length() && protectedAt(original, end)) {
                    end += Character.charCount(original.codePointAt(end));
                }
                values.add(original.substring(offset, end));
                text.append(prefix).append(values.size() - 1).append("__");
            } else {
                text.appendCodePoint(cp);
            }
            offset = end;
        }
        return new ProtectedText(original, text.toString(), prefix, List.copyOf(values));
    }

    public static boolean sameSymbols(String first, String second) {
        return signature(first).equals(signature(second));
    }

    private static String signature(String text) {
        return String.join("", protect(text).values);
    }

    private static boolean protectedAt(String text, int offset) {
        int cp = text.codePointAt(offset);
        int type = Character.getType(cp);
        if (cp == '\n' || cp == '\r' || cp == '~' || cp == '～' || cp == '〜'
                || cp == 0x200D || cp == 0xFE0E || cp == 0xFE0F || cp == 0x20E3
                || (cp >= 0x1F000 && cp <= 0x1FAFF) || (cp >= 0xE0020 && cp <= 0xE007F)
                || type == Character.OTHER_SYMBOL || type == Character.MATH_SYMBOL
                || type == Character.MODIFIER_SYMBOL) return true;
        // Protect the digit/#/* only when it actually begins a keycap sequence.
        if ((cp >= '0' && cp <= '9') || cp == '#' || cp == '*') {
            int next = offset + Character.charCount(cp);
            if (next < text.length() && text.codePointAt(next) == 0xFE0F) next++;
            return next < text.length() && text.codePointAt(next) == 0x20E3;
        }
        return false;
    }

    public static final class ProtectedText {
        private final String original, text, prefix;
        private final List<String> values;

        private ProtectedText(String original, String text, String prefix, List<String> values) {
            this.original = original;
            this.text = text;
            this.prefix = prefix;
            this.values = values;
        }

        public String text() { return text; }

        /** Reject missing, repeated, reordered or invented symbols instead of silently repairing. */
        public String restore(String translated) {
            Objects.requireNonNull(translated);
            StringBuilder restored = new StringBuilder();
            int cursor = 0;
            for (int i = 0; i < values.size(); i++) {
                String token = prefix + i + "__";
                int position = translated.indexOf(token, cursor);
                if (position < 0 || translated.indexOf(token, position + token.length()) >= 0) {
                    throw new IllegalArgumentException("SYMBOL_TOKEN_MISMATCH");
                }
                String between = translated.substring(cursor, position);
                if (between.contains(prefix)) throw new IllegalArgumentException("SYMBOL_TOKEN_ORDER");
                restored.append(between).append(values.get(i));
                cursor = position + token.length();
            }
            String tail = translated.substring(cursor);
            if (tail.contains(prefix)) throw new IllegalArgumentException("UNKNOWN_SYMBOL_TOKEN");
            restored.append(tail);
            if (!sameSymbols(original, restored.toString())) {
                throw new IllegalArgumentException("SYMBOL_MISMATCH");
            }
            return restored.toString();
        }
    }
}
