package com.stego;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Steganography Demo application.
 *
 * What this app does:
 *  1. Takes a PNG image + secret message + password via REST API.
 *  2. Encrypts the message with AES (password-derived key).
 *  3. Hides the encrypted bytes inside the image using LSB steganography.
 *  4. Returns the stego-image to the caller.
 *
 *  Reverse: supply stego-image + password → get secret message back.
 */
@SpringBootApplication
public class SteganographyApplication {

    public static void main(String[] args) {
        SpringApplication.run(SteganographyApplication.class, args);
    }
}
