package com.fanli.sakurazakatranslator.capture;

import java.util.List;
import static com.fanli.sakurazakatranslator.capture.ProbeModels.*;

public final class MessageHeaderDetectorSelfTest {
    private static int checks;

    public static void main(String[] args) {
        var nodes = List.of(
                node("row1", null, "LinearLayout", null, 80, 210, 210, true),
                node("name1", "row1", "TextView", "小田倉 麗奈", 100, 105, 118, false),
                node("time1", "row1", "TextView", "9/21 17:33", 100, 120, 130, false),
                node("body1", "row1", "TextView", "お腹いっぱい食べちゃって眠くなってきた", 130, 130, 185, false),
                node("row2", null, "LinearLayout", null, 235, 365, 365, true),
                node("name2", "row2", "TextView", "小田倉 麗奈", 100, 250, 263, false),
                node("time2", "row2", "TextView", "9/21 18:55", 100, 265, 275, false),
                node("body2", "row2", "TextView", "あのさあのさ、💗", 130, 275, 320, false),
                node("menu2", "row2", "Button", "•••", 290, 265, 280, true));
        var blocks = MessageHeaderDetector.detect(nodes, List.of("小田倉 麗奈"), 1);
        check(blocks.size() == 2, "two member-time headers create two message blocks");
        check(blocks.get(0).bodyNodeIds().equals(List.of("body1")), "first body belongs to first header");
        check(!blocks.get(0).bodyNodeIds().contains("time1"), "time is metadata only");
        check(blocks.get(1).bodyNodeIds().equals(List.of("body2")), "menu and metadata stay outside body");
        check(blocks.get(0).confidence() == MessageHeaderDetector.Confidence.HIGH,
                "complete same-row message is high confidence");
        check(MessageHeaderDetector.isTime("9/21 18:55"), "date and time format is accepted");
        check(!MessageHeaderDetector.isTime("18:55 menu"), "button text is not a time");
        System.out.println("MessageHeaderDetectorSelfTest PASS (" + checks + " checks)");
    }

    private static NodeRecord node(String id, String parent, String cls, String text,
                                   int left, int top, int bottom, boolean item) {
        return new NodeRecord(id, parent, 0, top, 1, text, null, cls, null,
                new Bounds(left, top, 320, bottom), null, true, cls.equals("Button"), item);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
