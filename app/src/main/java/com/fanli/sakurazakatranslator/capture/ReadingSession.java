package com.fanli.sakurazakatranslator.capture;

import com.fanli.sakurazakatranslator.domain.ChatMessage;
import com.fanli.sakurazakatranslator.domain.TranslationRequest;
import com.fanli.sakurazakatranslator.domain.TranslationResult;
import com.fanli.sakurazakatranslator.domain.TranslationValidator;

/** Completed in-memory snapshot. Navigation has no provider, screenshot or network dependency. */
public final class ReadingSession {
    private TranslationRequest request;
    private TranslationResult result;
    private int index;
    private int[][] scrollPositions = new int[0][2];
    private boolean original, stale;

    public boolean open(TranslationRequest input, TranslationResult output) {
        clear();
        TranslationResult validated = TranslationValidator.validate(input, output);
        if (!validated.accepted) return false;
        request = input;
        result = validated;
        scrollPositions = new int[input.messages.size()][2];
        return true;
    }

    public boolean hasResult() { return request != null; }
    public int index() { return index; }
    public int size() { return hasResult() ? request.messages.size() : 0; }
    public boolean isOriginal() { return original; }
    public boolean isStale() { return stale; }
    public void markStale() { stale = true; }
    public ChatMessage message() { return request.messages.get(index); }
    public String profileName() { return request.style == null ? "中文翻译" : request.style.displayName; }
    public String text() { return original ? message().originalText : result.translations.get(index).text(); }
    public int scroll() { return hasResult() ? scrollPositions[index][original ? 1 : 0] : 0; }
    public void rememberScroll(int y) {
        if (hasResult()) scrollPositions[index][original ? 1 : 0] = Math.max(0, y);
    }
    public void toggleOriginal() { original = !original; }
    public void move(int delta) {
        if (!hasResult()) return;
        index = Math.max(0, Math.min(size() - 1, index + delta));
        original = false;
    }
    public void clear() {
        request = null;
        result = null;
        index = 0;
        original = false;
        stale = false;
        scrollPositions = new int[0][2];
    }
}
