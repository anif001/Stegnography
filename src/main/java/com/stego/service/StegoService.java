package com.stego.service;

import com.stego.util.CryptoUtil;
import com.stego.util.SteganographyUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * StegoService — Orchestrates the encode and decode workflows.
 *
 * Encode pipeline:
 *   plain-text message
 *       │
 *       ▼ CryptoUtil.encrypt(password)
 *   Base64 cipher-text
 *       │
 *       ▼ SteganographyUtil.encode(image)
 *   stego-image bytes (PNG)
 *
 * Decode pipeline:
 *   stego-image
 *       │
 *       ▼ SteganographyUtil.decode()
 *   Base64 cipher-text bytes
 *       │
 *       ▼ CryptoUtil.decrypt(password)
 *   original plain-text message
 */
@Service
public class StegoService {

    /**
     * Hide an AES-encrypted version of {@code message} inside the supplied image.
     *
     * @param imageFile  the carrier PNG image (MultipartFile upload)
     * @param message    the secret text to hide
     * @param password   password used to derive the AES encryption key
     * @return           PNG image bytes with the hidden message embedded
     * @throws Exception on I/O, crypto, or capacity errors
     */
    public byte[] encodeMessage(MultipartFile imageFile, String message, String password)
            throws Exception {

        // ── 1. Read the uploaded image into a BufferedImage ───────────────
        BufferedImage originalImage = readImage(imageFile);

        // ── 2. Encrypt the message with AES using the supplied password ───
        //  Result is a Base64 string — safe to treat as plain text bytes.
        String encryptedMessage = CryptoUtil.encrypt(message, password);

        // ── 3. Convert encrypted string to bytes for embedding ────────────
        byte[] payloadBytes = encryptedMessage.getBytes("UTF-8");

        // ── 4. Embed payload bytes into the image via LSB steganography ───
        BufferedImage stegoImage = SteganographyUtil.encode(originalImage, payloadBytes);

        // ── 5. Convert the stego-image back to PNG bytes for the response ─
        return toPngBytes(stegoImage);
    }

    /**
     * Extract and decrypt the hidden message from a stego-image.
     *
     * @param imageFile  the stego PNG image (MultipartFile upload)
     * @param password   the same password used during encoding
     * @return           the original plain-text secret message
     * @throws Exception on I/O, crypto, or format errors;
     *                   a wrong password triggers a BadPaddingException wrapped here
     */
    public String decodeMessage(MultipartFile imageFile, String password) throws Exception {

        // ── 1. Read the stego-image ───────────────────────────────────────
        BufferedImage stegoImage = readImage(imageFile);

        // ── 2. Extract the raw payload bytes from the image's LSBs ────────
        byte[] payloadBytes = SteganographyUtil.decode(stegoImage);

        // ── 3. Convert bytes back to the Base64 cipher-text string ────────
        String encryptedMessage = new String(payloadBytes, "UTF-8");

        // ── 4. Decrypt — will throw if password is wrong ───────────────────
        //  BadPaddingException / IllegalBlockSizeException → bad password.
        //  The controller catches this and returns a 400 error response.
        return CryptoUtil.decrypt(encryptedMessage, password);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Read a MultipartFile as a BufferedImage.
     * Validates that the file is non-empty and readable as an image.
     */
    private BufferedImage readImage(MultipartFile imageFile) throws IOException {
        if (imageFile == null || imageFile.isEmpty()) {
            throw new IllegalArgumentException("No image file provided.");
        }

        try (InputStream inputStream = imageFile.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new IllegalArgumentException(
                    "Cannot read image. Make sure the file is a valid PNG.");
            }
            return image;
        }
    }

    /**
     * Serialize a BufferedImage to raw PNG bytes (in-memory, no disk I/O).
     */
    private byte[] toPngBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // "png" tells ImageIO to use the PNG encoder
        boolean written = ImageIO.write(image, "png", baos);
        if (!written) {
            throw new IOException("Failed to write image as PNG.");
        }
        return baos.toByteArray();
    }
}
