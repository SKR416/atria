package io.github.skr416.atria.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM field cipher (ADR-0004 section 4).
 * Output format: {@code {v1}<Base64(IV(12) + Ciphertext + AuthTag(16))>} with a fresh random 96-bit IV per call.
 * The KEK is a Base64-encoded 32-byte key injected via {@code ATRIA_CRYPTO_KEK}.
 * The version prefix leaves room for future key rotation.
 */
@Component
public class AesGcmCipher {

    public static final String CURRENT_VERSION_PREFIX = "{v1}";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmCipher(@Value("${atria.crypto.kek:}") String base64Kek) {
        if (base64Kek == null || base64Kek.isBlank()) {
            throw new IllegalStateException("atria.crypto.kek (ATRIA_CRYPTO_KEK) must be configured");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Kek.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ATRIA_CRYPTO_KEK must be valid Base64", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException("ATRIA_CRYPTO_KEK must decode to exactly 32 bytes (AES-256), got " + raw.length);
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + ct.length);
            buf.put(iv).put(ct);
            return CURRENT_VERSION_PREFIX + Base64.getEncoder().encodeToString(buf.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Field encryption failed", e);
        }
    }

    /** Fails closed: a value without a known version prefix, or with a bad auth tag, is rejected. */
    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(CURRENT_VERSION_PREFIX)) {
            throw new IllegalStateException("Encrypted field has unknown or missing version prefix");
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(CURRENT_VERSION_PREFIX.length()));
            if (all.length < IV_LENGTH + TAG_BITS / 8) {
                throw new IllegalStateException("Encrypted field is truncated");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_LENGTH));
            byte[] pt = cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Field decryption failed (wrong key or tampered data)", e);
        }
    }
}
