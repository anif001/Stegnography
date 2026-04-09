# Steganography Demo — Spring Boot

**LSB Steganography + AES Encryption (256-bit key, JVM dependent)**
Hides secret messages inside PNG images using encryption + data hiding.

---

## How It Works

```
ENCODE:
  plaintext message
      │
      ▼  AES (PBKDF2 key from password)
  Base64 ciphertext
      │
      ▼  LSB steganography into PNG pixels
  stego-image  ← returned to caller

DECODE:
  stego-image
      │
      ▼  LSB extraction from pixels
  Base64 ciphertext
      │
      ▼  AES decryption (same password)
  original plaintext message
```

### LSB Technique (Least Significant Bit)

Each pixel has R, G, B channels (0–255 each). The last bit (LSB) changes colour by only ±1 — invisible to the human eye.

* 3 bits per pixel (R, G, B)
* First 32 bits → payload length
* Remaining bits → encrypted message

---

## Architecture

```
Client → Controller → Service → Util
                         ├── CryptoUtil (AES encryption/decryption)
                         └── SteganographyUtil (LSB encoding/decoding)
```

---

## Project Structure

```
src/main/java/com/stego/
├── SteganographyApplication.java
├── controller/
│   └── StegoController.java
├── service/
│   └── StegoService.java
└── util/
    ├── CryptoUtil.java
    └── SteganographyUtil.java
```

---

## Running the Application

**Prerequisites:** Java 21, Maven 3.8+

```bash
mvn clean package
java -jar target/steganography-demo-1.0.0.jar
```

Server: http://localhost:8080

---

## API Endpoints

### POST /encode

| Field    | Type | Description         |
| -------- | ---- | ------------------- |
| image    | File | PNG image           |
| message  | Text | Secret message      |
| password | Text | Encryption password |

Response: PNG image (stego-image)

---

### POST /decode

| Field    | Type | Description   |
| -------- | ---- | ------------- |
| image    | File | Stego image   |
| password | Text | Same password |

Response: Original message

---

## Curl Usage (Alternative to Postman)

```bash
curl -X POST http://localhost:8080/encode \
  -F "image=@input.png" \
  -F "message=Hello" \
  -F "password=secret" \
  --output stego.png
```

---

## Postman Instructions

### Encode

* POST → http://localhost:8080/encode
* Body → form-data:

   * image (file)
   * message (text)
   * password (text)
* Save response as PNG

### Decode

* POST → http://localhost:8080/decode
* Body → form-data:

   * image (file)
   * password (text)

---

## Demo

* Input Image → original.png
* Output Image → stego_output.png
* Decoded Message → "Meet me at midnight"

---

## Image Capacity

| Image Size | Max Message |
| ---------- | ----------- |
| 100×100    | ~3.7 KB     |
| 512×512    | ~98 KB      |
| 1920×1080  | ~777 KB     |

---

## Security Details

| Feature        | Implementation                           |
| -------------- | ---------------------------------------- |
| Encryption     | AES (CBC, PKCS5Padding)                  |
| Key Derivation | PBKDF2WithHmacSHA256 (65,536 iterations) |
| Steganography  | LSB (3 bits per pixel)                   |
| Wrong Password | Returns 400 error                        |
| Image Format   | PNG only                                 |

---

## Error Handling

* Wrong password → 400 Bad Request
* Image too small → 400 Bad Request
* Invalid file → 400 Bad Request

---

## Limitations

* Supports only PNG images
* No compression before embedding
* Large messages require large images
* No authentication system

---

## Tech Stack

* Java 21
* Spring Boot
* Maven

---

