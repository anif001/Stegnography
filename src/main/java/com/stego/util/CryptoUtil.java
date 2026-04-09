package com.stego.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * CryptoUtil — AES-256 encryption / decryption helper.
 *
 * Key design choices:
 *  • We use PBKDF2 (Password-Based Key Derivation Function 2) to turn any
 *    user-supplied password into a fixed 256-bit AES key. This is safer than
 *    padding/truncating the password directly.
 *  • A fixed salt is used here for simplicity (exam scope). In production you
 *    would embed a random salt alongside the cipher-text.
 *  • AES/CBC/PKCS5Padding — CBC mode requires a 16-byte Initialisation Vector
 *    (IV). We use a fixed IV here for simplicity; production code would store
 *    a random IV with the payload.
 */
public class CryptoUtil {

    // ── Constants ────────────────────────────────────────────────────────────

    /** AES key length in bits — 256 gives the strongest standard AES variant. */
    private static final int KEY_LENGTH_BITS = 256;

    /** PBKDF2 iteration count — higher = slower brute-force. 65 536 is a common default. */
    private static final int PBKDF2_ITERATIONS = 65_536;

    /**
     * Fixed salt for key derivation.
     * NOTE: In production, generate a random salt per message and store it
     *       alongside the cipher-text so both sides can derive the same key.
     */
    private static final byte[] FIXED_SALT = "StegoSalt@ANITS!".getBytes();

    /**
     * Fixed 16-byte IV for AES/CBC.
     * NOTE: Same caveat as the salt — random IV per message in real apps.
     */
    private static final byte[] FIXED_IV = "StegoIV@16bytes!".getBytes(); // exactly 16 bytes

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Encrypt a plain-text string using AES-256-CBC with a password.
     *
     * @param plainText  the message to encrypt
     * @param password   user-supplied password (any length)
     * @return           Base64-encoded cipher-text string
     * @throws Exception on any crypto error
     */
    public static String encrypt(String plainText, String password) throws Exception {
        // 1. Derive a 256-bit AES key from the password
        SecretKey secretKey = deriveKey(password);

        // 2. Create AES/CBC cipher and initialise for encryption
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        IvParameterSpec ivSpec = new IvParameterSpec(FIXED_IV);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);

        // 3. Encrypt the UTF-8 bytes of the plain text
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes("UTF-8"));

        // 4. Return as Base64 string (safe to embed as text / in bit-stream)
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    /**
     * Decrypt a Base64-encoded cipher-text using AES-256-CBC with a password.
     *
     * @param cipherTextBase64  Base64-encoded cipher-text (output of encrypt())
     * @param password          same password used during encryption
     * @return                  original plain-text string
     * @throws Exception        if the password is wrong or data is corrupted
     */
    public static String decrypt(String cipherTextBase64, String password) throws Exception {
        // 1. Derive the same key from the same password
        SecretKey secretKey = deriveKey(password);

        // 2. Decode Base64 back to raw cipher bytes
        byte[] cipherBytes = Base64.getDecoder().decode(cipherTextBase64);

        // 3. Create AES/CBC cipher and initialise for decryption
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        IvParameterSpec ivSpec = new IvParameterSpec(FIXED_IV);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);

        // 4. Decrypt — this will throw a BadPaddingException if the password is wrong
        byte[] decryptedBytes = cipher.doFinal(cipherBytes);

        // 5. Convert decrypted bytes back to a UTF-8 string
        return new String(decryptedBytes, "UTF-8");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Derive a 256-bit AES SecretKey from a plain-text password using PBKDF2.
     *
     * @param password  the user-supplied password
     * @return          SecretKeySpec ready for AES operations
     */
    private static SecretKey deriveKey(String password) throws Exception {
        // PBKDF2 with HMAC-SHA256 — standard password-to-key algorithm
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

        KeySpec spec = new PBEKeySpec(
                password.toCharArray(), // password as char array
                FIXED_SALT,             // salt bytes
                PBKDF2_ITERATIONS,      // iteration count
                KEY_LENGTH_BITS         // desired key size
        );

        // Generate the raw key bytes and wrap them in an AES SecretKeySpec
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }
}
