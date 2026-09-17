package com.fanli.sakurazakatranslator.translation;

import com.fanli.sakurazakatranslator.domain.*;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** In-memory HTTP boundary: these tests never contact a server or use real credentials. */
public final class DeepSeekProviderTest {
    private TranslationRequest request() {
        return new TranslationRequest(new PageToken(1, "test.app", 1, 1),
                List.of(new ChatMessage("m1", "ありがとう💗", "NODE_TEXT", 0, 0, 10, 10,
                        "SCREEN", List.of("n1"), List.of())), null, true);
    }

    @Test public void sendsOnlyProtectedTextAndDoesNotFollowRedirects() throws Exception {
        FakeConnection http = new FakeConnection(200);
        var provider = new DeepSeekProvider("test-key", "deepseek-chat", () -> http);
        var result = provider.translate(request());
        assertTrue(result.accepted);
        assertEquals("谢谢💗", result.translations.get(0).text());
        assertEquals("Bearer test-key", http.getRequestProperty("Authorization"));
        assertFalse(http.getInstanceFollowRedirects());
        assertEquals("POST", http.getRequestMethod());
        assertTrue(http.disconnected);
        assertFalse(http.body.toString(StandardCharsets.UTF_8).contains("test-key"));
        assertFalse(http.body.toString(StandardCharsets.UTF_8).contains("image_url"));
    }

    @Test public void redirectAndRateLimitDoNotLeakServerBody() throws Exception {
        for (int status : new int[]{302, 429}) {
            FakeConnection http = new FakeConnection(status);
            var provider = new DeepSeekProvider("test-key", "deepseek-chat", () -> http);
            IOException error = assertThrows(IOException.class, () -> provider.translate(request()));
            assertEquals(status == 302 ? "REDIRECT" : "RATE_LIMIT", error.getMessage());
            assertFalse(http.readBody);
            assertTrue(http.disconnected);
        }
    }

    @Test public void canceledBeforeStartDoesNotOpenConnection() throws Exception {
        var provider = new DeepSeekProvider("test-key", "deepseek-chat", () -> {
            throw new AssertionError("opened after cancellation");
        });
        provider.cancel();
        assertEquals("CANCELED", assertThrows(IOException.class,
                () -> provider.translate(request())).getMessage());
    }

    @Test public void oversizedResponseAndHeaderInjectionRejected() throws Exception {
        FakeConnection http = new FakeConnection(200);
        http.oversized = true;
        var provider = new DeepSeekProvider("test-key", "deepseek-chat", () -> http);
        assertEquals("RESPONSE_LIMIT", assertThrows(IOException.class,
                () -> provider.translate(request())).getMessage());
        assertTrue(http.disconnected);
        assertThrows(IllegalArgumentException.class, () -> new DeepSeekProvider("key\r\nInjected: yes", "model"));
    }

    private static final class FakeConnection extends HttpsURLConnection {
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        final int status;
        boolean disconnected, readBody, oversized;
        FakeConnection(int status) throws Exception {
            super(new URL("https://api.deepseek.com/chat/completions"));
            this.status = status;
        }
        @Override public OutputStream getOutputStream() { return body; }
        @Override public int getResponseCode() { return status; }
        @Override public InputStream getInputStream() throws IOException {
            readBody = true;
            if (oversized) return new ByteArrayInputStream(new byte[128 * 1024 + 1]);
            try {
                String content = new JSONObject(body.toString(StandardCharsets.UTF_8))
                        .getJSONArray("messages").getJSONObject(1).getString("content");
                String input = new JSONObject(content).getJSONArray("messages").getJSONObject(0).getString("text");
                String translated = new JSONObject().put("translations", new JSONArray().put(
                        new JSONObject().put("id", "m1").put("text", input.replace("ありがとう", "谢谢")))).toString();
                String response = new JSONObject().put("choices", new JSONArray().put(new JSONObject()
                        .put("finish_reason", "stop").put("message", new JSONObject().put("content", translated)))).toString();
                return new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));
            } catch (JSONException e) { throw new IOException(e); }
        }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
        @Override public String getCipherSuite() { return ""; }
        @Override public Certificate[] getLocalCertificates() { return null; }
        @Override public Certificate[] getServerCertificates() { return new Certificate[0]; }
    }
}
