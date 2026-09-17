package com.fanli.sakurazakatranslator.capture;

import java.util.List;
import java.util.Set;

public final class ConfirmedMessageFactorySelfTest {
    public static void main(String[] args) {
        ProbeModels.Bounds firstBounds = new ProbeModels.Bounds(0, 100, 100, 130);
        ProbeModels.Bounds secondBounds = new ProbeModels.Bounds(0, 50, 100, 80);
        ProbeModels.TextFragment body = new ProbeModels.TextFragment(
                "body", "正文～", "正文～", ProbeModels.Source.NODE_TEXT,
                ProbeModels.Role.BODY, null, firstBounds, List.of(), List.of(), "body", false);
        ProbeModels.TextFragment metadata = new ProbeModels.TextFragment(
                "time", "9/14 20:00", "9/14 20:00", ProbeModels.Source.NODE_TEXT,
                ProbeModels.Role.METADATA, null, secondBounds, List.of(), List.of(), "metadata", false);
        List<com.fanli.sakurazakatranslator.domain.ChatMessage> messages =
                ConfirmedMessageFactory.fromFragments(List.of(body, metadata), Set.of("body"));
        check(messages.size() == 1, "unselected metadata must not enter translation messages");
        check(messages.get(0).originalText.equals("正文～"), "body text must remain unchanged");
        System.out.println("ConfirmedMessageFactorySelfTest PASS");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
