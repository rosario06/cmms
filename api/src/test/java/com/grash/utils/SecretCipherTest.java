package com.grash.utils;

import com.grash.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretCipherTest {

    // 32 bytes of test data, not a real key
    private static final String KEY_A = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String KEY_B;

    static {
        byte[] other = new byte[32];
        other[0] = 1;
        KEY_B = Base64.getEncoder().encodeToString(other);
    }

    @Test
    void roundTrip_returnsOriginalValue() {
        SecretCipher cipher = new SecretCipher(KEY_A);

        String encrypted = cipher.encrypt("sk_test_abc123");

        assertTrue(encrypted.startsWith("v1:"));
        assertFalse(encrypted.contains("sk_test_abc123"));
        assertEquals("sk_test_abc123", cipher.decrypt(encrypted));
    }

    @Test
    void roundTrip_supportsUnicodeAndEmptyString() {
        SecretCipher cipher = new SecretCipher(KEY_A);

        assertEquals("contraseña-ñ-€", cipher.decrypt(cipher.encrypt("contraseña-ñ-€")));
        assertEquals("", cipher.decrypt(cipher.encrypt("")));
    }

    @Test
    void encrypt_usesDifferentIvEachTime() {
        SecretCipher cipher = new SecretCipher(KEY_A);

        String first = cipher.encrypt("same value");
        String second = cipher.encrypt("same value");

        assertNotEquals(first, second);
        assertEquals("same value", cipher.decrypt(first));
        assertEquals("same value", cipher.decrypt(second));
    }

    @Test
    void missingKey_encryptFailsWithClearMessage() {
        for (String key : new String[]{null, "", "   "}) {
            SecretCipher cipher = new SecretCipher(key);
            assertFalse(cipher.isConfigured());

            CustomException ex = assertThrows(CustomException.class, () -> cipher.encrypt("secret"));

            assertEquals("INTEGRATIONS_ENCRYPTION_KEY no configurada", ex.getMessage());
            assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        }
    }

    @Test
    void invalidKey_isTreatedAsNotConfigured() {
        String notBase64 = "%%%not-base64%%%";
        String wrongLength = Base64.getEncoder().encodeToString(new byte[16]);

        for (String key : new String[]{notBase64, wrongLength}) {
            SecretCipher cipher = new SecretCipher(key);
            assertFalse(cipher.isConfigured());
            CustomException ex = assertThrows(CustomException.class, () -> cipher.encrypt("secret"));
            assertEquals("INTEGRATIONS_ENCRYPTION_KEY no configurada", ex.getMessage());
        }
    }

    @Test
    void missingKey_decryptFails() {
        SecretCipher configured = new SecretCipher(KEY_A);
        String encrypted = configured.encrypt("secret");
        SecretCipher unconfigured = new SecretCipher("");

        CustomException ex = assertThrows(CustomException.class, () -> unconfigured.decrypt(encrypted));

        assertEquals("INTEGRATIONS_ENCRYPTION_KEY no configurada", ex.getMessage());
    }

    @Test
    void tamperedCiphertext_isRejectedWithoutLeakingContent() {
        SecretCipher cipher = new SecretCipher(KEY_A);
        String encrypted = cipher.encrypt("top-secret-value");
        byte[] raw = Base64.getDecoder().decode(encrypted.substring(3));
        raw[raw.length - 1] ^= 0x01;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);

        CustomException ex = assertThrows(CustomException.class, () -> cipher.decrypt(tampered));

        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        assertFalse(ex.getMessage().contains("top-secret-value"));
    }

    @Test
    void wrongKey_isRejected() {
        String encrypted = new SecretCipher(KEY_A).encrypt("secret");
        SecretCipher other = new SecretCipher(KEY_B);

        assertThrows(CustomException.class, () -> other.decrypt(encrypted));
    }

    @Test
    void malformedInput_isRejected() {
        SecretCipher cipher = new SecretCipher(KEY_A);

        assertThrows(CustomException.class, () -> cipher.decrypt("plain text"));
        assertThrows(CustomException.class, () -> cipher.decrypt("v1:!!!not base64!!!"));
        assertThrows(CustomException.class, () -> cipher.decrypt("v1:" + Base64.getEncoder().encodeToString(new byte[5])));
        assertThrows(CustomException.class, () -> cipher.decrypt(null));
    }

    @Test
    void last4_returnsLastFourCharacters() {
        assertEquals("6789", SecretCipher.last4("sk_test_123456789"));
        assertEquals("abc", SecretCipher.last4("abc"));
        assertNull(SecretCipher.last4(null));
    }
}
