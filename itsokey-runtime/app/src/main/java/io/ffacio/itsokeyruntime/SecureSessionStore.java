package io.ffacio.itsokeyruntime;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecureSessionStore {
    private static final String PREFS = "itsokey_runtime_secure";
    private static final String VALUE_KEY = "session_v2";
    private static final String LEGACY_VALUE_KEY = "session_v1";
    private static final String KEY_ALIAS = "itsokey_runtime_session_key_v1";
    private static final int GCM_TAG_BITS = 128;
    private final SharedPreferences prefs;

    SecureSessionStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized ItsokeySession load() {
        String encoded = prefs.getString(VALUE_KEY, prefs.getString(LEGACY_VALUE_KEY, null));
        if (encoded == null || encoded.trim().isEmpty()) return null;
        try {
            byte[] packed = Base64.decode(encoded, Base64.NO_WRAP);
            if (packed.length < 13) throw new IllegalStateException("암호화 저장값이 손상되었습니다");
            int ivLength = packed[0] & 0xff;
            if (ivLength < 12 || 1 + ivLength >= packed.length) throw new IllegalStateException("암호화 IV가 올바르지 않습니다");
            byte[] iv = new byte[ivLength];
            byte[] ciphertext = new byte[packed.length - 1 - ivLength];
            System.arraycopy(packed, 1, iv, 0, ivLength);
            System.arraycopy(packed, 1 + ivLength, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            ItsokeySession session = ItsokeySession.fromJson(new JSONObject(new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)));
            if (prefs.contains(LEGACY_VALUE_KEY) && !prefs.contains(VALUE_KEY)) save(session);
            return session;
        } catch (Exception error) {
            clear();
            return null;
        }
    }

    synchronized void save(ItsokeySession session) throws Exception {
        if (session == null || !session.usable()) throw new IllegalArgumentException("완전한 ITSOKEY 세션이 필요합니다");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] ciphertext = cipher.doFinal(session.toJson(true).toString().getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();
        byte[] packed = new byte[1 + iv.length + ciphertext.length];
        packed[0] = (byte) iv.length;
        System.arraycopy(iv, 0, packed, 1, iv.length);
        System.arraycopy(ciphertext, 0, packed, 1 + iv.length, ciphertext.length);
        if (!prefs.edit().putString(VALUE_KEY, Base64.encodeToString(packed, Base64.NO_WRAP)).remove(LEGACY_VALUE_KEY).commit()) {
            throw new IllegalStateException("세션 저장에 실패했습니다");
        }
    }

    synchronized void clear() {
        prefs.edit().remove(VALUE_KEY).remove(LEGACY_VALUE_KEY).commit();
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
