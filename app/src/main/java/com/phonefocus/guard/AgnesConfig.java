package com.phonefocus.guard;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Personal credential encrypted by a device-local Android Keystore key, excluded from backup. */
final class AgnesConfig {
    private static final String ALIAS="phonefocus_agnes_v1",FIELD="encrypted_config";
    final String key,model;
    private AgnesConfig(String key,String model) { this.key=key; this.model=model; }
    static boolean present(Context c) { return EntertainmentSettings.prefs(c).contains(FIELD); }
    private static SecretKey encryptionKey(boolean create) throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey)store.getKey(ALIAS,null);
        if (!create) throw new IllegalStateException("Configuration key unavailable");
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    static void save(Context c,String key,String model) throws Exception {
        key=key.trim();
        if (key.length()<12 || key.length()>2048 || !key.matches("[A-Za-z0-9._-]+") || !AgnesProtocol.validModel(model)) throw new IllegalArgumentException("Invalid Agnes configuration");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,encryptionKey(true));
        byte[] data=new JSONObject().put("api_key",key).put("model",model).toString().getBytes(StandardCharsets.UTF_8);
        String packed=new JSONObject().put("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
            .put("ciphertext",Base64.encodeToString(cipher.doFinal(data),Base64.NO_WRAP)).toString();
        EntertainmentSettings.setEnabled(c,false);
        if (!EntertainmentSettings.prefs(c).edit().putString(FIELD,packed).commit()) throw new IllegalStateException("Configuration not saved");
    }
    static AgnesConfig read(Context c) throws Exception {
        JSONObject packed=new JSONObject(EntertainmentSettings.prefs(c).getString(FIELD,""));
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,encryptionKey(false),new GCMParameterSpec(128,Base64.decode(packed.getString("iv"),Base64.NO_WRAP)));
        JSONObject value=new JSONObject(new String(cipher.doFinal(Base64.decode(packed.getString("ciphertext"),Base64.NO_WRAP)),StandardCharsets.UTF_8));
        String model=value.getString("model");if (!AgnesProtocol.validModel(model)) throw new IllegalArgumentException("Invalid model");
        return new AgnesConfig(value.getString("api_key"),model);
    }
    static void clear(Context c) {
        EntertainmentSettings.setEnabled(c,false);EntertainmentSettings.prefs(c).edit().remove(FIELD).apply();
        try { KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);store.deleteEntry(ALIAS); } catch (Exception ignored) { }
    }
}
