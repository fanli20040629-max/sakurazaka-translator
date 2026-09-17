package com.fanli.sakurazakatranslator.translation;

import com.fanli.sakurazakatranslator.domain.*;
import java.io.*;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONException;
import static com.fanli.sakurazakatranslator.translation.TranslationRunner.DeepSeekFailure;

/** Text-only DeepSeek adapter. A fresh instance belongs to one explicit send. */
public final class DeepSeekProvider implements TranslationProvider {
    private static final int MAX_BYTES = 128 * 1024;
    private final String apiKey, model;
    private final ConnectionSource connections;
    private volatile boolean canceled;
    private volatile HttpsURLConnection connection;

    public DeepSeekProvider(String apiKey, String model) {
        this(apiKey, model, () -> (HttpsURLConnection)
                new URL("https://api.deepseek.com/chat/completions").openConnection());
    }

    @FunctionalInterface interface ConnectionSource {
        HttpsURLConnection open() throws IOException;
    }

    DeepSeekProvider(String apiKey, String model, ConnectionSource connections) {
        if (apiKey == null || apiKey.isBlank() || apiKey.length() > 512
                || apiKey.chars().anyMatch(c -> c < 33 || c > 126)) {
            throw new IllegalArgumentException("KEY_INVALID");
        }
        this.apiKey = apiKey;
        this.model = model;
        this.connections = java.util.Objects.requireNonNull(connections);
    }

    @Override public TranslationResult translate(TranslationRequest request) throws IOException {
        if (canceled) throw new DeepSeekFailure("CANCELED");
        HttpsURLConnection http = null;
        try {
            var prepared = TranslationProtocol.prepare(request, model);
            byte[] body = prepared.body().getBytes(StandardCharsets.UTF_8);
            if (body.length > MAX_BYTES) throw new DeepSeekFailure("INPUT_LIMIT");
            http = connections.open();
            connection = http;
            http.setConnectTimeout(10_000);
            http.setReadTimeout(25_000);
            http.setInstanceFollowRedirects(false);
            http.setUseCaches(false);
            http.setRequestMethod("POST");
            http.setRequestProperty("Authorization", "Bearer " + apiKey);
            http.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            http.setRequestProperty("Accept", "application/json");
            http.setDoOutput(true);
            http.setFixedLengthStreamingMode(body.length);
            if (canceled) throw new DeepSeekFailure("CANCELED");
            try (OutputStream output = http.getOutputStream()) { output.write(body); }
            int status = http.getResponseCode();
            if (status != 200) {
                // Never follow redirects or expose server bodies (which can echo source text).
                throw new DeepSeekFailure(status == 401 || status == 403 ? "AUTH"
                        : status == 402 ? "BALANCE" : status == 429 ? "RATE_LIMIT"
                        : status >= 300 && status < 400 ? "REDIRECT"
                        : status >= 500 ? "SERVER" : "REQUEST");
            }
            byte[] response;
            try (InputStream input = http.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (canceled) throw new DeepSeekFailure("CANCELED");
                    if (output.size() + count > MAX_BYTES) throw new DeepSeekFailure("RESPONSE_LIMIT");
                    output.write(buffer, 0, count);
                }
                response = output.toByteArray();
            }
            if (canceled) throw new DeepSeekFailure("CANCELED");
            return prepared.parse(new String(response, StandardCharsets.UTF_8));
        } catch (SocketTimeoutException e) {
            throw new DeepSeekFailure("TIMEOUT");
        } catch (IllegalArgumentException e) {
            throw new DeepSeekFailure("INPUT_LIMIT".equals(e.getMessage()) ? "INPUT_LIMIT" : "VALIDATION");
        } catch (JSONException e) {
            throw new DeepSeekFailure("VALIDATION");
        } finally {
            connection = null;
            if (http != null) http.disconnect();
        }
    }

    /** Called off the UI thread by the runner. Already sent data cannot be recalled. */
    @Override public void cancel() {
        canceled = true;
        HttpsURLConnection http = connection;
        if (http != null) http.disconnect();
    }
}
