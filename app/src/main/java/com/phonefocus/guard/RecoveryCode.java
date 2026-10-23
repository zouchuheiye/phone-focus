package com.phonefocus.guard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;

/** App emergency release secret; never a system lock-screen password. Only its digest is persisted. */
public final class RecoveryCode {
    private RecoveryCode() {}
    public static String generate() {
        byte[] bytes = new byte[16]; new SecureRandom().nextBytes(bytes);
        String raw = hex(bytes).toUpperCase(Locale.ROOT);
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < raw.length(); i += 4) { if (i > 0) formatted.append('-'); formatted.append(raw, i, i+4); }
        return formatted.toString();
    }
    private static String normalized(String code) {
        if (code == null) throw new IllegalArgumentException("Missing recovery code");
        String raw = code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (!raw.matches("[0-9A-F]{32}")) throw new IllegalArgumentException("Invalid recovery code");
        return raw;
    }
    public static String hash(String code) {
        try { return hex(MessageDigest.getInstance("SHA-256").digest(normalized(code).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static boolean matches(String code, String expectedHash) {
        if (expectedHash == null || !expectedHash.matches("[0-9a-f]{64}")) return false;
        try { return MessageDigest.isEqual(hash(code).getBytes(StandardCharsets.US_ASCII), expectedHash.getBytes(StandardCharsets.US_ASCII)); }
        catch (IllegalArgumentException invalid) { return false; }
    }
    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length*2);
        for (byte b : bytes) result.append(String.format(Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }
}
