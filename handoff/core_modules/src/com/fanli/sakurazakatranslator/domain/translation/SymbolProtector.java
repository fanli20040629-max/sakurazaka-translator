package com.fanli.sakurazakatranslator.domain.translation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 校验译文符号的重复次数和相对顺序；不修改、补写或猜测译文。
 * 不是图片/Emoji 分类器，也不能自动识别所有颜文字。
 * 上游确认的颜文字可通过 literalSpans 指定，整个片段会原样校验。
 */
public final class SymbolProtector {
    private SymbolProtector() { }

    public static ProtectedText protect(String source) {
        return protect(source, List.of());
    }

    /** source 保持原样；仅比较时把 CRLF/CR 统一为 LF。不发送网络请求。 */
    public static ProtectedText protect(String source, List<String> literalSpans) {
        Objects.requireNonNull(source, "source");
        List<String> literals = new ArrayList<>(List.copyOf(literalSpans));
        for (String literal : literals) {
            if (literal.isEmpty() || !source.contains(literal)) {
                throw new IllegalArgumentException("保护片段必须非空且出现在原文中");
            }
        }
        // 长片段优先，避免较短颜文字抢先匹配掉完整片段。
        literals.replaceAll(SymbolProtector::normalizeLines);
        literals.sort(Comparator.comparingInt(String::length).reversed());
        return new ProtectedText(source, List.copyOf(literals), scan(source, literals));
    }

    public static Validation validate(ProtectedText expected, String translated) {
        Objects.requireNonNull(expected, "expected");
        if (translated == null) return new Validation(false, expected.requiredSymbols(), List.of());
        List<String> actual = scan(translated, expected.literalSpans);
        return new Validation(expected.requiredSymbols().equals(actual), expected.requiredSymbols(), actual);
    }

    private static List<String> scan(String text, List<String> literals) {
        String normalized = normalizeLines(text);
        List<String> symbols = new ArrayList<>();
        for (int index = 0; index < normalized.length();) {
            String matched = null;
            for (String literal : literals) {
                if (normalized.startsWith(literal, index)) {
                    matched = literal;
                    break;
                }
            }
            if (matched != null) {
                symbols.add(matched);
                index += matched.length();
                continue;
            }
            int cp = normalized.codePointAt(index);
            // 键帽 Emoji 的数字也是整体的一部分，不能只保护组合修饰符。
            int following = index + Character.charCount(cp);
            boolean keycap = "0123456789#*".indexOf(cp) >= 0
                    && (normalized.startsWith("\u20e3", following)
                    || normalized.startsWith("\ufe0f\u20e3", following));
            if (isProtected(cp) || keycap) symbols.add(new String(Character.toChars(cp)));
            index += Character.charCount(cp);
        }
        return List.copyOf(symbols);
    }

    private static boolean isProtected(int cp) {
        // 保守范围，含旗帜、肤色和常见装饰符；并非完整 Unicode Emoji 标准表。
        return cp == '\n' || cp == '~' || cp == '～' || cp == '〜'
                || cp == '!' || cp == '?' || cp == '！' || cp == '？'
                || cp == 0x200d || cp == 0x20e3 || cp == 0xfe0e || cp == 0xfe0f
                || cp == 0x00a9 || cp == 0x00ae || cp == 0x3030 || cp == 0x303d
                || cp == 0x3297 || cp == 0x3299
                || (cp >= 0x2300 && cp <= 0x27ff)
                || (cp >= 0x1f000 && cp <= 0x1faff)
                || (cp >= 0xe0020 && cp <= 0xe007f);
    }

    private static String normalizeLines(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    /** 只能由 protect 创建，避免调用方伪造符号清单；列表不可修改。 */
    public static final class ProtectedText {
        private final String source;
        private final List<String> literalSpans;
        private final List<String> requiredSymbols;

        private ProtectedText(String source, List<String> literalSpans, List<String> requiredSymbols) {
            this.source = source;
            this.literalSpans = literalSpans;
            this.requiredSymbols = requiredSymbols;
        }

        public String source() { return source; }
        public List<String> requiredSymbols() { return requiredSymbols; }
    }

    /** 不一致时交回总协调器决定至多一次重试或显示原文待确认。 */
    public record Validation(boolean isValid, List<String> expectedSymbols, List<String> actualSymbols) {
        public Validation {
            expectedSymbols = List.copyOf(expectedSymbols);
            actualSymbols = List.copyOf(actualSymbols);
        }
    }
}
