package com.stego.controller;

import com.stego.service.StegoService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;



/**
 * StegoController — Exposes the two steganography REST endpoints.
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  POST /encode                                                       │
 * │    Form params: image (file), message (text), password (text)       │
 * │    Response:    PNG image bytes (Content-Type: image/png)           │
 * ├─────────────────────────────────────────────────────────────────────┤
 * │  POST /decode                                                       │
 * │    Form params: image (file), password (text)                       │
 * │    Response:    extracted message as plain text                     │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * Both endpoints accept multipart/form-data.
 * Test with Postman — see README / comments below for exact instructions.
 */
@RestController
@RequestMapping("/")          // base path; endpoints are /encode and /decode
public class StegoController {


    @GetMapping("/test")
    public String test() {
        return "API is working";
    }

    private final StegoService stegoService;

    // Constructor injection — Spring wires the StegoService bean automatically
    public StegoController(StegoService stegoService) {
        this.stegoService = stegoService;
    }

    // ── Encode Endpoint ───────────────────────────────────────────────────────

    /**
     * POST /encode
     *
     * Hides the (AES-encrypted) message inside the provided PNG image.
     *
     * Postman setup:
     *   Method : POST
     *   URL    : http://localhost:8080/encode
     *   Body   : form-data
     *              Key: image    | Type: File   | Value: <select a .png file>
     *              Key: message  | Type: Text   | Value: Hello, World!
     *              Key: password | Type: Text   | Value: mySecret123
     *   Save response as file → you get the stego PNG.
     *
     * @param image     carrier PNG image uploaded by the client
     * @param message   the secret text to hide
     * @param password  password for AES encryption
     * @return          stego-image as PNG bytes, or 400 on validation error,
     *                  500 on unexpected error
     */
    @PostMapping(value = "/encode", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> encode(
            @RequestPart("image")    MultipartFile image,
            @RequestParam("message") String message,
            @RequestParam("password") String password) {

        // ── Input validation ───────────────────────────────────────────────
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body("Error: 'message' cannot be empty.");
        }
        if (password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body("Error: 'password' cannot be empty.");
        }

        try {
            // Delegate to the service layer
            byte[] stegoImageBytes = stegoService.encodeMessage(image, message, password);

            // Build response: raw PNG bytes with correct Content-Type
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            // Suggest a filename so Postman / browser can save it conveniently
            headers.setContentDispositionFormData("attachment", "stego_output.png");

            return new ResponseEntity<>(stegoImageBytes, headers, HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            // Covers "image too small" and "invalid input" cases
            return ResponseEntity.badRequest().body("Encode Error: " + e.getMessage());

        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected encode error: " + e.getMessage());
        }
    }

    // ── Decode Endpoint ───────────────────────────────────────────────────────

    /**
     * POST /decode
     *
     * Extracts and decrypts the hidden message from a stego-image.
     *
     * Postman setup:
     *   Method : POST
     *   URL    : http://localhost:8080/decode
     *   Body   : form-data
     *              Key: image    | Type: File   | Value: <stego PNG from /encode>
     *              Key: password | Type: Text   | Value: mySecret123
     *   Response: plain text with the secret message.
     *
     * @param image     stego PNG image
     * @param password  password for AES decryption (must match encode password)
     * @return          recovered plain-text message, or 400 on wrong password / bad data
     */
    @PostMapping(value = "/decode", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> decode(
            @RequestPart("image")    MultipartFile image,
            @RequestParam("password") String password) {

        // ── Input validation ───────────────────────────────────────────────
        if (password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body("Error: 'password' cannot be empty.");
        }

        try {
            String secretMessage = stegoService.decodeMessage(image, password);
            return ResponseEntity.ok(secretMessage);

        } catch (IllegalStateException e) {
            // Covers "invalid payload length" — image has no hidden data
            return ResponseEntity.badRequest()
                    .body("Decode Error: " + e.getMessage());

        } catch (javax.crypto.BadPaddingException | javax.crypto.IllegalBlockSizeException e) {
            // AES decryption failed — almost always means a wrong password
            return ResponseEntity.badRequest()
                    .body("Decode Error: Wrong password or corrupted data. " +
                          "Please check your password and try again.");

        } catch (Exception e) {
            // Catch-all: includes wrong password wrapped in other exception types
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (msg.toLowerCase().contains("pad") || msg.toLowerCase().contains("decrypt")) {
                return ResponseEntity.badRequest()
                        .body("Decode Error: Wrong password. Cannot decrypt the hidden message.");
            }
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unexpected decode error: " + msg);
        }
    }
}
