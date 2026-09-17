package com.fanli.sakurazakatranslator.domain;

import java.util.ArrayList;
import java.util.List;

/** Desktop tests live outside app/src/main so they are not shipped in the APK. */
public final class DomainSelfTest {
    public static void main(String[] args) {
        PageToken page = new PageToken(1, "test.app", 7, 0);
        ChatMessage first = new ChatMessage("m2", "第二行♡", "NODE_TEXT", 10, 200, 80, 220,
                "SCREEN", List.of("n2"), List.of());
        ChatMessage second = new ChatMessage("m1", "第一行～", "NODE_TEXT", 10, 100, 80, 120,
                "SCREEN", List.of("n1"), List.of());
        List<ChatMessage> source = new ArrayList<>(List.of(first, second));
        TranslationRequest request = new TranslationRequest(page, source,
                new StyleProfile("idol-a", "示例偶像", "保留轻柔语气"), true);
        source.clear();
        check(request.messages.size() == 2, "request must own an immutable message snapshot");

        List<ChatMessage> selected = MessageSelection.confirmed(request.messages, List.of("m2", "m1"));
        check(selected.size() == 2, "both selected messages must remain");
        check(selected.get(0).id.equals("m1"), "selection must sort top to bottom");

        TranslationResult providerResult = new TranslationResult(List.of(
                new TranslationResult.Item("m1", "第一行～"), new TranslationResult.Item("m2", "第二行♡")),
                List.of(), true);
        TranslationResult accepted = TranslationValidator.validate(request, providerResult);
        check(accepted.accepted, "matching confirmed result must be accepted");

        TranslationRequest unconfirmed = new TranslationRequest(page, request.messages, request.style, false);
        TranslationResult rejected = TranslationValidator.validate(unconfirmed, providerResult);
        check(!rejected.accepted && rejected.warnings.contains("INPUT_NOT_CONFIRMED"),
                "unconfirmed input must be rejected");
        var mutableTranslations = new ArrayList<>(List.of(new TranslationResult.Item("m1", "翻译")));
        var mutableWarnings = new ArrayList<>(List.of("PROVIDER_WARNING"));
        var snapshot = new TranslationResult(mutableTranslations, mutableWarnings, true);
        mutableTranslations.clear();
        mutableWarnings.clear();
        check(snapshot.translations.size() == 1 && snapshot.warnings.size() == 1,
                "result owns its lists");
        check(TranslationValidator.validate(request, snapshot).warnings.contains("PROVIDER_WARNING"),
                "provider warning survives validation");
        check(!TranslationValidator.validate(null, snapshot).accepted, "missing request rejected");
        check(!TranslationValidator.validate(request, null).accepted, "missing result rejected");
        System.out.println("DomainSelfTest PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
