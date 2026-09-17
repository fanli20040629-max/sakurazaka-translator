package com.fanli.sakurazakatranslator.translation;

import com.fanli.sakurazakatranslator.domain.*;
import org.json.*;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public final class TranslationProtocolTest {
    private TranslationRequest request() {
        return new TranslationRequest(new PageToken(1, "test.app", 1, 1),
                List.of(new ChatMessage("n1", "ありがとう～💗\nまたね", "NODE_TEXT",
                        0, 0, 40, 60, "SCREEN", List.of("n1"), List.of())),
                new StyleProfile("one", "测试", "自然口语，不能增加亲密称呼"), true);
    }

    @Test public void protectedPayloadRoundTrip() throws Exception {
        var prepared = TranslationProtocol.prepare(request(), "deepseek-chat");
        JSONObject payload = new JSONObject(prepared.body());
        assertEquals("json_object", payload.getJSONObject("response_format").getString("type"));
        String user = payload.getJSONArray("messages").getJSONObject(1).getString("content");
        String source = new JSONObject(user).getJSONArray("messages").getJSONObject(0).getString("text");
        assertFalse(source.contains("💗"));
        var result = prepared.parse(response("n1", source.replace("ありがとう", "谢谢").replace("またね", "再见"), "stop"));
        assertTrue(result.accepted);
        assertEquals("谢谢～💗\n再见", result.translations.get(0).text());
    }

    @Test public void damagedSymbolsRejected() throws Exception {
        var p = TranslationProtocol.prepare(request(), "deepseek-chat");
        assertThrows(IllegalArgumentException.class, () -> p.parse(response("n1", "谢谢", "stop")));
    }

    @Test public void wrongIdAndTruncatedOutputRejected() throws Exception {
        var p = TranslationProtocol.prepare(request(), "deepseek-chat");
        assertThrows(IllegalArgumentException.class, () -> p.parse(response("wrong", "谢谢", "stop")));
        assertThrows(IllegalArgumentException.class, () -> p.parse(response("n1", "谢谢", "length")));
    }

    @Test public void invalidConfigurationAndUnconfirmedInputRejected() {
        assertThrows(IllegalArgumentException.class, () -> TranslationProtocol.prepare(request(), "bad\nmodel"));
        var original = request();
        var unconfirmed = new TranslationRequest(original.pageToken, original.messages, original.style, false);
        assertThrows(IllegalArgumentException.class, () -> TranslationProtocol.prepare(unconfirmed, "deepseek-chat"));
    }

    @Test public void nonStringTranslationIsRejected() throws Exception {
        var prepared = TranslationProtocol.prepare(request(), "deepseek-chat");
        String content = new JSONObject().put("translations", new JSONArray()
                .put(new JSONObject().put("id", "n1").put("text", 123))).toString();
        String response = new JSONObject().put("choices", new JSONArray().put(new JSONObject()
                .put("finish_reason", "stop").put("message", new JSONObject().put("content", content)))).toString();
        assertThrows(Exception.class, () -> prepared.parse(response));
    }

    private static String response(String id, String text, String reason) throws JSONException {
        String content = new JSONObject().put("translations", new JSONArray()
                .put(new JSONObject().put("id", id).put("text", text))).toString();
        return new JSONObject().put("choices", new JSONArray().put(new JSONObject()
                .put("finish_reason", reason).put("message", new JSONObject().put("content", content)))).toString();
    }
}
