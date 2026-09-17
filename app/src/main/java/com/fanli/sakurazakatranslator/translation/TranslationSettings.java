package com.fanli.sakurazakatranslator.translation;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import com.fanli.sakurazakatranslator.domain.StyleProfile;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores profiles normally, API credentials only as Keystore-encrypted ciphertext. */
public final class TranslationSettings {
    private static final String ALIAS = "sakurazaka.translation.key.v1";
    private final SharedPreferences preferences;

    public TranslationSettings(Context context) {
        preferences = context.getSharedPreferences("translation_settings", Context.MODE_PRIVATE);
    }

    public String model() { return preferences.getString("model", "deepseek-chat"); }
    public int selectedProfile() { return preferences.getInt("selected_profile", 0) == 1 ? 1 : 0; }
    public boolean hasKey() { return preferences.contains("key_ciphertext"); }

    public StyleProfile profile(int index) {
        String fallback = index == 0 ? "偶像一" : "偶像二";
        return new StyleProfile("idol-" + index,
                preferences.getString("profile_name_" + index, fallback),
                preferences.getString("profile_guidance_" + index,
                        "自然口语，保留原文礼貌程度，不增加原文没有的亲密称呼。"));
    }

    public String readKey() throws GeneralSecurityException, IOException {
        if (!hasKey()) return "";
        byte[] iv = Base64.decode(preferences.getString("key_iv", ""), Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(preferences.getString("key_ciphertext", ""), Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Blank key preserves the existing credential. Removal is an explicit, separate action. */
    public void save(String key, String model, int selected, StyleProfile[] profiles)
            throws GeneralSecurityException, IOException {
        if (model == null || !model.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")
                || profiles.length != 2 || selected < 0 || selected > 1) {
            throw new IllegalArgumentException("CONFIGURATION");
        }
        for (StyleProfile profile : profiles) {
            if (profile.displayName.length() > 60 || profile.guidance.length() > 2000) {
                throw new IllegalArgumentException("STYLE_LIMIT");
            }
        }
        SharedPreferences.Editor editor = preferences.edit().putString("model", model)
                .putInt("selected_profile", selected);
        for (int i = 0; i < profiles.length; i++) {
            editor.putString("profile_name_" + i, profiles[i].displayName);
            editor.putString("profile_guidance_" + i, profiles[i].guidance);
        }
        if (key != null && !key.isBlank()) {
            if (key.length() > 512 || key.chars().anyMatch(c -> c < 33 || c > 126)) {
                throw new IllegalArgumentException("KEY_INVALID");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey());
            byte[] encrypted = cipher.doFinal(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            editor.putString("key_iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .putString("key_ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP));
        }
        if (!editor.commit()) throw new IOException("SETTINGS_WRITE_FAILED");
    }

    public void removeKey() throws IOException {
        if (!preferences.edit().remove("key_iv").remove("key_ciphertext").commit()) {
            throw new IOException("SETTINGS_WRITE_FAILED");
        }
    }

    private static synchronized SecretKey encryptionKey() throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }
}
