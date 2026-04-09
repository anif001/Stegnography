package com.stego.util;

import java.awt.image.BufferedImage;

/**
 * SteganographyUtil — Least-Significant-Bit (LSB) image steganography.
 *
 * ── How LSB steganography works ─────────────────────────────────────────────
 *
 *  Every pixel in an RGB image stores three colour channels: Red, Green, Blue.
 *  Each channel is an 8-bit value (0–255).  The least-significant bit (bit 0)
 *  contributes only ±1 to the colour value — a change invisible to the human eye.
 *
 *  We exploit this by replacing the LSB of each channel with one bit of our
 *  secret data.  Three channels × 1 bit = 3 bits per pixel.
 *
 *  Layout of hidden data in the image:
 *  ┌──────────────────────────────┬────────────────────────────────────────┐
 *  │  First 32 bits (≈11 pixels) │  Payload length as a 4-byte int        │
 *  │  Next N×8 bits              │  N bytes of encrypted-message payload  │
 *  └──────────────────────────────┴────────────────────────────────────────┘
 *
 *  Storing the length first lets the decoder know exactly how many bytes to
 *  read — no delimiter scanning needed.
 *
 * ── Capacity limit ──────────────────────────────────────────────────────────
 *  Max payload bytes = (width × height × 3) / 8  minus 4 bytes for the length
 *  header.  A 512×512 image can hold ~98 304 bytes of payload.
 */
public class SteganographyUtil {

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Encode (hide) a payload byte-array inside a copy of the given image.
     *
     * @param original     source PNG as a BufferedImage
     * @param payloadBytes the secret data to hide (already encrypted)
     * @return             a new BufferedImage with the payload hidden in it
     * @throws IllegalArgumentException if the image is too small for the payload
     */
    public static BufferedImage encode(BufferedImage original, byte[] payloadBytes) {
        int width  = original.getWidth();
        int height = original.getHeight();

        // Calculate maximum bytes this image can store
        int maxBytes = (width * height * 3) / 8 - 4; // subtract 4 for length header
        if (payloadBytes.length > maxBytes) {
            throw new IllegalArgumentException(
                "Image too small! Can hold " + maxBytes +
                " bytes but payload is " + payloadBytes.length + " bytes."
            );
        }

        // Work on a fresh copy so we don't mutate the original
        BufferedImage stego = deepCopy(original);

        // ── Build the full bit-stream: [4-byte length][payload bytes] ──────
        byte[] lengthHeader = intToBytes(payloadBytes.length); // 4 bytes, big-endian
        byte[] fullData = concat(lengthHeader, payloadBytes);  // length + payload

        // Convert entire data into a stream of individual bits
        boolean[] bits = toBitArray(fullData);

        // ── Embed bits into image LSBs ────────────────────────────────────
        int bitIndex = 0;

        outer:
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                // Get the current pixel's packed RGB integer
                int pixel = stego.getRGB(x, y);

                // Extract R, G, B channels (each 0–255)
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >>  8) & 0xFF;
                int b =  pixel        & 0xFF;

                // Replace the LSB of each channel with the next data bit
                if (bitIndex < bits.length) r = setLSB(r, bits[bitIndex++]);
                if (bitIndex < bits.length) g = setLSB(g, bits[bitIndex++]);
                if (bitIndex < bits.length) b = setLSB(b, bits[bitIndex++]);

                // Reassemble and write back the modified pixel
                int newPixel = (0xFF << 24) | (r << 16) | (g << 8) | b;
                stego.setRGB(x, y, newPixel);

                // All bits written — stop early
                if (bitIndex >= bits.length) break outer;
            }
        }

        return stego;
    }

    /**
     * Decode (extract) the hidden payload bytes from a stego-image.
     *
     * @param stego  the stego-image produced by encode()
     * @return       the raw payload bytes (still encrypted; caller decrypts them)
     */
    public static byte[] decode(BufferedImage stego) {
        int width  = stego.getWidth();
        int height = stego.getHeight();

        // ── Step 1: Extract the first 32 bits to read the payload length ──
        //  32 bits = 4 bytes (int), stored in the LSBs of the first ~11 pixels
        boolean[] lengthBits = extractBits(stego, 0, 32, width, height);
        byte[] lengthBytes = toByteArray(lengthBits);
        int payloadLength = bytesToInt(lengthBytes);

        // Sanity-check: reject obviously invalid lengths
        int maxPossible = (width * height * 3) / 8 - 4;
        if (payloadLength <= 0 || payloadLength > maxPossible) {
            throw new IllegalStateException(
                "Invalid payload length detected (" + payloadLength +
                "). The image may not contain hidden data, or the password is wrong."
            );
        }

        // ── Step 2: Extract the actual payload bits (skip first 32 bits) ──
        int payloadBitCount = payloadLength * 8;
        boolean[] payloadBits = extractBits(stego, 32, payloadBitCount, width, height);

        // Convert extracted bits back to bytes and return
        return toByteArray(payloadBits);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Extract a sequence of bits from the image's pixel LSBs.
     *
     * @param image       the image to read from
     * @param startBit    global bit offset to start at
     * @param count       how many bits to extract
     * @param width       image width
     * @param height      image height
     * @return            boolean array of extracted bits (MSB first per byte)
     */
    private static boolean[] extractBits(BufferedImage image,
                                          int startBit, int count,
                                          int width, int height) {
        boolean[] bits = new boolean[count];
        int bitIndex = 0;
        int globalBit = 0; // running counter across all channel-bits we visit

        outer:
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getRGB(x, y);

                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >>  8) & 0xFF;
                int b =  pixel        & 0xFF;

                // Process all three channel LSBs in order
                int[] channels = { r, g, b };
                for (int ch : channels) {
                    if (globalBit >= startBit) {
                        // This global bit falls within our extraction window
                        bits[bitIndex++] = (ch & 1) == 1; // read the LSB
                        if (bitIndex >= count) break outer;
                    }
                    globalBit++;
                }
            }
        }

        return bits;
    }

    /**
     * Set the least-significant bit of a byte value to the given flag.
     *
     * @param value  original channel value (0–255)
     * @param bit    true → set LSB to 1, false → set LSB to 0
     * @return       modified channel value
     */
    private static int setLSB(int value, boolean bit) {
        // Clear the LSB then OR in the desired bit
        return bit ? (value | 1) : (value & ~1);
    }

    /**
     * Convert a byte array to a flat boolean array of bits (MSB first per byte).
     * e.g. 0b10110000 → [true, false, true, true, false, false, false, false]
     */
    private static boolean[] toBitArray(byte[] data) {
        boolean[] bits = new boolean[data.length * 8];
        for (int i = 0; i < data.length; i++) {
            for (int bit = 7; bit >= 0; bit--) {
                // Bit 7 is MSB; we store MSB first (index 0 of each byte-group)
                bits[i * 8 + (7 - bit)] = ((data[i] >> bit) & 1) == 1;
            }
        }
        return bits;
    }

    /**
     * Convert a flat boolean bit array (MSB first per byte) back to bytes.
     * Inverse of toBitArray().
     */
    private static byte[] toByteArray(boolean[] bits) {
        byte[] data = new byte[bits.length / 8];
        for (int i = 0; i < data.length; i++) {
            for (int bit = 0; bit < 8; bit++) {
                // MSB is at bit index 0 of each group
                if (bits[i * 8 + bit]) {
                    data[i] |= (byte) (1 << (7 - bit));
                }
            }
        }
        return data;
    }

    /**
     * Convert an int to a 4-byte big-endian byte array.
     * Used to store the payload length as the first 4 bytes of the bit-stream.
     */
    private static byte[] intToBytes(int value) {
        return new byte[] {
            (byte) (value >> 24),
            (byte) (value >> 16),
            (byte) (value >>  8),
            (byte)  value
        };
    }

    /**
     * Read a big-endian int from the first 4 bytes of an array.
     * Inverse of intToBytes().
     */
    private static int bytesToInt(byte[] b) {
        return ((b[0] & 0xFF) << 24)
             | ((b[1] & 0xFF) << 16)
             | ((b[2] & 0xFF) <<  8)
             |  (b[3] & 0xFF);
    }

    /** Concatenate two byte arrays into one. */
    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    /**
     * Create a deep (pixel-data) copy of a BufferedImage so we never mutate
     * the caller's image reference.
     */
    private static BufferedImage deepCopy(BufferedImage src) {
        BufferedImage copy = new BufferedImage(
                src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        copy.getGraphics().drawImage(src, 0, 0, null);
        return copy;
    }
}
