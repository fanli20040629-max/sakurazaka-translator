package com.fanli.sakurazakatranslator.domain;

import java.util.List;

public final class TranslationRequestFactorySelfTest {
    public static void main(String[] args) {
        ChatMessage message = new ChatMessage("m1", "こんにちは～", "NODE_TEXT", 20, 10, 100, 40,
                "SCREEN", List.of("n1"), List.of());
        TranslationRequest request = TranslationRequestFactory.confirmed(
                new PageToken(1, "test.app", 7, 0), List.of(message),
                new StyleProfile("default", "默认", "保留语气和符号"));
        check(request.userConfirmed, "factory output must be confirmed");
        check(request.messages.get(0).originalText.equals("こんにちは～"),
                "factory must preserve original text");
        System.out.println("TranslationRequestFactorySelfTest PASS");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
