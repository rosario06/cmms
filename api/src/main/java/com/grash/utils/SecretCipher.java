package com.grash.utils;

import com.grash.exception.CustomException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts integration secrets at rest with AES-256-GCM.
 * Format: "v1:" + base64(iv || ciphertext+tag). A random 12-byte IV is generated per value.
 * The key comes from the property integrations.encryption-key (base64 of 32 bytes).
 * When the key is missing or invalid, encrypt/decrypt fail and nothing is ever stored in clear.
 */
@Component
public class SecretCipher {

    public static final String KEY_MISSING_MESSAGE = "INTEGRATIONS_ENCRYPTION_KEY no configurada";
    static final String PREFIX = "v1:";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec key;

    public SecretCipher(@Value("${integrations.encryption-key:}") String encodedKey) {
        this.key = parseKey(encodedKey);
    }

    private static SecretKeySpec parseKey(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(encodedKey.trim());
            if (bytes.length != KEY_BYTES) {
                return null;
            }
            return new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isConfigured() {
        return key != null;
    }

    public String encrypt(String plain) {
        requireKey();
        if (plain == null) {
            throw new CustomException("Secret to encrypt is null", HttpStatus.BAD_REQUEST);
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_BYTES + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_BYTES);
            System.arraycopy(ct, 0, out, IV_BYTES, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new CustomException("No se pudo cifrar el secreto", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public String decrypt(String encrypted) {
        requireKey();
        if (encrypted == null || !encrypted.startsWith(PREFIX)) {
            throw new CustomException("Secreto cifrado inválido", HttpStatus.CONFLICT);
        }
        try {
            byte[] all = Base64.getDecoder().decode(encrypted.substring(PREFIX.length()));
            if (all.length <= IV_BYTES) {
                throw new CustomException("Secreto cifrado inválido", HttpStatus.CONFLICT);
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // never include the payload in the message
            throw new CustomException("Secreto cifrado inválido o clave de cifrado incorrecta", HttpStatus.CONFLICT);
        }
    }

    /**
     * Last 4 characters of the value (used to display a masked secret).
     */
    public static String last4(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 4 ? value : value.substring(value.length() - 4);
    }

    private void requireKey() {
        if (key == null) {
            throw new CustomException(KEY_MISSING_MESSAGE, HttpStatus.CONFLICT);
        }
    }
}
