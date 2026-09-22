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
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores profiles normally, API credentials only as Keystore-encrypted ciphertext. */
public final class TranslationSettings {
    public static final int MAX_PROFILES = 20;
    private static final String ALIAS = "sakurazaka.translation.key.v1";
    private final SharedPreferences preferences;

    public TranslationSettings(Context context) {
        preferences = context.getSharedPreferences("translation_settings", Context.MODE_PRIVATE);
    }

    public String model() { return preferences.getString("model", "deepseek-chat"); }
    public int selectedProfile() {
        return Math.max(0, Math.min(profileCount() - 1, preferences.getInt("selected_profile", 0)));
    }
    public boolean hasKey() { return preferences.contains("key_ciphertext"); }
    public boolean quickTranslation() { return preferences.getBoolean("quick_translation", false); }

    public StyleProfile profile(int index) {
        String fallback = index == 0 ? "偶像一" : index == 1 ? "偶像二" : "成员档案 " + (index + 1);
        String aliases = preferences.getString("profile_aliases_" + index, "");
        return new StyleProfile("idol-" + index,
                preferences.getString("profile_name_" + index, fallback),
                preferences.getString("profile_guidance_" + index,
                        "自然口语，保留原文礼貌程度，不增加原文没有的亲密称呼。"),
                aliases.lines().map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    private int profileCount() {
        // Absence is the old two-profile format; loading never rewrites the user's data.
        return Math.max(1, Math.min(MAX_PROFILES, preferences.getInt("profile_count", 2)));
    }

    public List<StyleProfile> profiles() {
        List<StyleProfile> profiles = new ArrayList<>();
        for (int i = 0; i < profileCount(); i++) profiles.add(profile(i));
        return List.copyOf(profiles);
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
    public void save(String key, String model, int selected, StyleProfile[] profiles, boolean quickTranslation)
            throws GeneralSecurityException, IOException {
        if (model == null || !model.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")
                || profiles == null || profiles.length < 1 || profiles.length > MAX_PROFILES
                || selected < 0 || selected >= profiles.length) {
            throw new IllegalArgumentException("CONFIGURATION");
        }
        for (StyleProfile profile : profiles) {
            if (profile.displayName.length() > 60 || profile.guidance.length() > 2000) {
                throw new IllegalArgumentException("STYLE_LIMIT");
            }
        }
        SharedPreferences.Editor editor = preferences.edit().putString("model", model)
                .putInt("profile_count", profiles.length).putInt("selected_profile", selected)
                .putBoolean("quick_translation", quickTranslation);
        for (int i = 0; i < profiles.length; i++) {
            editor.putString("profile_name_" + i, profiles[i].displayName);
            editor.putString("profile_guidance_" + i, profiles[i].guidance);
            editor.putString("profile_aliases_" + i, String.join("\n", profiles[i].aliases));
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
