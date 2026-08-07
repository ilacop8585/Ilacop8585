package com.nexus.universalbridge.finalapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecretStore {
    private static final String PREFS = "nexus_bridge_identity";
    private static final String KEY_ALIAS = "nexus_bridge_identity_key_v1";
    private static final String K_DEVICE = "device_id";
    private static final String K_TOKEN = "token_ciphertext";
    private static final String K_IV = "token_iv";
    private final SharedPreferences prefs;

    public SecretStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized String getOrCreateDeviceId() {
        String id = prefs.getString(K_DEVICE, null);
        if (id != null && id.length() >= 8) return id;
        id = UUID.randomUUID().toString();
        prefs.edit().putString(K_DEVICE, id).apply();
        return id;
    }

    public synchronized String getOrCreateToken() throws Exception {
        String enc = prefs.getString(K_TOKEN, null);
        String iv = prefs.getString(K_IV, null);
        if (enc != null && iv != null) {
            try {
                return decrypt(enc, iv);
            } catch (Exception ignored) {
                prefs.edit().remove(K_TOKEN).remove(K_IV).apply();
            }
        }
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        encryptAndStore(token);
        return token;
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) store.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private void encryptAndStore(String token) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] ciphertext = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        prefs.edit()
                .putString(K_TOKEN, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .putString(K_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .apply();
    }

    private String decrypt(String encoded, String encodedIv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = Base64.decode(encodedIv, Base64.NO_WRAP);
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        byte[] clear = cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP));
        return new String(clear, StandardCharsets.UTF_8);
    }
}
