package com.fanli.sakurazakatranslator.capture;

import com.fanli.sakurazakatranslator.domain.*;
import java.util.List;

/** No Android mocks: verifies result identity, local navigation and stale-session retention. */
public final class ReadingSessionSelfTest {
    public static void main(String[] args) {
        ReadingSession session = new ReadingSession();
        ChatMessage first = message("first", "こんにちは～💗");
        ChatMessage second = message("second", "長文です\n".repeat(200));
        TranslationRequest request = TranslationRequestFactory.confirmed(new PageToken(1, "target", 5, 1),
                List.of(first, second), new StyleProfile("one", "偶像一", "保留语气"));
        TranslationResult result = new TranslationResult(List.of(
                new TranslationResult.Item("second", "这是长文\n".repeat(200)),
                new TranslationResult.Item("first", "你好～💗")), List.of(), true);
        check(session.open(request, result), "valid reordered result opens");
        check(session.text().equals("你好～💗"), "match by ID, not provider order");
        session.rememberScroll(83);
        session.toggleOriginal();
        check(session.text().equals(first.originalText) && session.scroll() == 0, "original independent");
        session.rememberScroll(21);
        session.toggleOriginal();
        check(session.scroll() == 83, "translation position retained");
        session.move(1);
        check(session.index() == 1 && !session.isOriginal() && session.scroll() == 0, "next starts correctly");
        check(session.text().length() > 500, "no long text truncation");
        session.move(1);
        check(session.index() == 1, "next clamped");
        session.move(-1);
        check(session.scroll() == 83, "previous restores reading position");
        session.markStale();
        check(session.isStale() && session.hasResult(), "scroll retains completed snapshot");
        session.clear();
        check(!session.hasResult(), "privacy clear releases result");
        check(!session.open(request, new TranslationResult(List.of(
                new TranslationResult.Item("wrong", "x")), List.of(), true)), "reject wrong IDs");
        check(!session.hasResult(), "invalid result not retained");
        check(!session.open(request, new TranslationResult(result.translations, List.of(), false)), "reject unaccepted");
        check(!session.open(request, new TranslationResult(List.of(
                new TranslationResult.Item("first", "你好"),
                new TranslationResult.Item("second", "长文")), List.of(), true)), "reject lost symbols");
        check(session.open(request, result) && !session.isStale(), "new session resets stale flag");
        check(session.open(ReadingDemo.request(new PageToken(8, "synthetic", 2, 1)), ReadingDemo.result()),
                "offline demo passes same validation as real results");
        session.move(2);
        check(session.text().length() > 600, "offline demo includes a genuinely scrollable long message");
        System.out.println("ReadingSessionSelfTest PASS (identity, symbols, navigation, long text, scroll, lifecycle)");
    }

    private static ChatMessage message(String id, String text) {
        return new ChatMessage(id, text, "NODE_TEXT", 30, 100, 350, 230,
                "SCREEN", List.of(id), List.of());
    }
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
