package com.fanli.sakurazakatranslator.capture;

import com.fanli.sakurazakatranslator.domain.*;
import java.util.Collections;
import java.util.List;

/** Fictional, local fixtures. The service exposes this only on its explicitly enabled synthetic page. */
public final class ReadingDemo {
    private ReadingDemo() { }
    private static final String LONG_JA = String.join("\n\n", Collections.nCopies(12,
            "今日はリハーサルでした。少し緊張したけど、みんなと一緒に頑張りました。"
                    + "いつも応援してくれてありがとう。明日も自分のペースで進んでいくね～💗"));
    private static final String LONG_ZH = String.join("\n\n", Collections.nCopies(12,
            "今天去彩排啦。虽然有一点紧张，不过和大家一起努力了。"
                    + "谢谢你们一直为我加油，每次想到这些都会觉得很温暖。"
                    + "明天也会按照自己的节奏，一点一点继续向前走呀～💗"));

    public static TranslationRequest request(PageToken page) {
        return TranslationRequestFactory.confirmed(page, List.of(
                message("demo-1", "今日もおつかれさま～💗"),
                message("demo-2", "お花を見つけたよ🌷"), message("demo-long", LONG_JA)),
                new StyleProfile("demo", "本地演示 · 非真实翻译", ""));
    }
    public static TranslationResult result() {
        return new TranslationResult(List.of(
                new TranslationResult.Item("demo-1", "今天也辛苦啦～💗"),
                new TranslationResult.Item("demo-2", "发现了小花呀🌷"),
                new TranslationResult.Item("demo-long", LONG_ZH)), List.of(), true);
    }
    private static ChatMessage message(String id, String text) {
        return new ChatMessage(id, text, "DEMO", 0, 0, 0, 0, "NONE", List.of("local-demo"), List.of());
    }
}
