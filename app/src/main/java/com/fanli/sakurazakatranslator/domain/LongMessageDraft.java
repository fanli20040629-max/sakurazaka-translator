package com.fanli.sakurazakatranslator.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Mutable, UI-owned draft for a user-confirmed message spanning several captures. */
public final class LongMessageDraft {
    public enum Outcome { STARTED, APPENDED, DUPLICATE, NO_OVERLAP, WRONG_CONTEXT, WRONG_SOURCE, LIMIT, EMPTY }

    private static final int MAX_LENGTH = 6000;
    private static final int MAX_PARTS = 12;
    private static final int MIN_OVERLAP_CODE_POINTS = 12;
    private static final int MIN_NON_WHITESPACE = 8;

    private String packageName;
    private int windowId;
    private String source;
    private String text = "";
    private int parts;
    private final Set<String> captures = new LinkedHashSet<>();
    private final Set<String> provenance = new LinkedHashSet<>();
    private final Set<String> warnings = new LinkedHashSet<>();

    /** Combines parts only after the UI has explicitly confirmed they are one OCR message. */
    public static ChatMessage selectedPart(List<ChatMessage> selected) {
        if (selected == null || selected.isEmpty()) throw new IllegalArgumentException("selected");
        if (selected.size() == 1) {
            if (selected.get(0) == null) throw new IllegalArgumentException("selected");
            return selected.get(0);
        }
        List<String> provenance = new ArrayList<>();
        LinkedHashSet<String> warnings = new LinkedHashSet<>();
        StringBuilder text = new StringBuilder();
        ChatMessage previous = null;
        for (ChatMessage part : selected) {
            if (part == null || !"OCR".equals(part.source) || !"IMAGE".equals(part.coordinateSpace)
                    || part.right <= part.left || part.bottom <= part.top
                    || part.warnings.contains("MANUAL_ROLE_OVERRIDE")
                    || part.warnings.contains("CROSS_SOURCE_UNVERIFIED")
                    || (previous != null && previous.bottom > part.top)) {
                throw new IllegalArgumentException("selected OCR parts");
            }
            if (text.length() > 0) text.append('\n');
            text.append(part.originalText);
            provenance.addAll(part.provenance);
            warnings.addAll(part.warnings);
            previous = part;
        }
        warnings.add("MANUAL_OCR_PART");
        return new ChatMessage("manual-ocr-part", text.toString(), "OCR", 0, 0, 0, 0, "NONE",
                List.copyOf(provenance), List.copyOf(warnings));
    }

    public Outcome append(PageToken page, ChatMessage part) {
        if (page == null || part == null || part.originalText == null || part.originalText.isBlank()) {
            return Outcome.EMPTY;
        }
        String incoming = part.originalText;
        if (isEmpty()) {
            if (incoming.length() > MAX_LENGTH) return Outcome.LIMIT;
            start(page, part);
            return Outcome.STARTED;
        }
        if (!packageName.equals(page.packageName()) || windowId != page.windowId()) {
            return Outcome.WRONG_CONTEXT;
        }
        if (!source.equals(part.source)) return Outcome.WRONG_SOURCE;
        String capture = captureKey(page);
        if (captures.contains(capture) || text.equals(incoming)) return Outcome.DUPLICATE;
        if (incoming.length() > MAX_LENGTH) return Outcome.LIMIT;
        int overlap = exactUnambiguousOverlap(text, incoming);
        if (overlap < 0) return Outcome.NO_OVERLAP;
        String suffix = incoming.substring(overlap);
        if (suffix.isEmpty()) return Outcome.NO_OVERLAP;
        if (parts >= MAX_PARTS || text.length() + suffix.length() > MAX_LENGTH) return Outcome.LIMIT;

        text += suffix;
        parts++;
        captures.add(capture);
        addMetadata(page, part);
        return Outcome.APPENDED;
    }

    public boolean isEmpty() { return parts == 0; }
    public String text() { return text; }
    public int parts() { return parts; }

    public void clear() {
        packageName = null;
        windowId = 0;
        source = null;
        text = "";
        parts = 0;
        captures.clear();
        provenance.clear();
        warnings.clear();
    }

    public ChatMessage message() {
        if (isEmpty()) throw new IllegalStateException("empty long-message draft");
        LinkedHashSet<String> outputWarnings = new LinkedHashSet<>(warnings);
        outputWarnings.add("MANUAL_MULTISCREEN");
        outputWarnings.add("COMPLETENESS_UNVERIFIED");
        return new ChatMessage("manual-long-message", text, source, 0, 0, 0, 0, "NONE",
                List.copyOf(provenance), List.copyOf(outputWarnings));
    }

    private void start(PageToken page, ChatMessage part) {
        packageName = page.packageName();
        windowId = page.windowId();
        source = part.source;
        text = part.originalText;
        parts = 1;
        captures.add(captureKey(page));
        addMetadata(page, part);
    }

    private void addMetadata(PageToken page, ChatMessage part) {
        String prefix = page.requestId() + ":" + page.pageEpoch() + ":";
        for (String item : part.provenance) provenance.add(prefix + item);
        warnings.addAll(part.warnings);
    }

    private static String captureKey(PageToken page) {
        return page.requestId() + ":" + page.pageEpoch();
    }

    private static int exactUnambiguousOverlap(String accumulated, String incoming) {
        int[] left = accumulated.codePoints().toArray();
        int[] right = incoming.codePoints().toArray();
        int maximum = Math.min(left.length, right.length);
        int matched = 0;
        int qualifying = 0;
        for (int length = maximum; length >= MIN_OVERLAP_CODE_POINTS; length--) {
            boolean equal = true;
            for (int i = 0; i < length; i++) {
                if (left[left.length - length + i] != right[i]) { equal = false; break; }
            }
            if (equal && nonWhitespace(right, length) >= MIN_NON_WHITESPACE) {
                if (matched == 0) matched = length;
                qualifying++;
            }
        }
        if (qualifying != 1) return -1;
        String overlap = new String(right, 0, matched);
        int terminal = accumulated.length() - overlap.length();
        if (accumulated.indexOf(overlap) != terminal || accumulated.lastIndexOf(overlap) != terminal) return -1;
        if (incoming.indexOf(overlap) != 0 || incoming.indexOf(overlap, 1) >= 0) return -1;
        return overlap.length();
    }

    private static int nonWhitespace(int[] codePoints, int length) {
        int result = 0;
        for (int i = 0; i < length; i++) if (!Character.isWhitespace(codePoints[i])) result++;
        return result;
    }
}
