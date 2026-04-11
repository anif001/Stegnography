# Steganography Demo — Spring Boot

**LSB Steganography + AES Encryption (256-bit key, JVM dependent)**
Hides secret messages inside PNG images using encryption + data hiding.

---

## 🌐 Live Application

🔗 **Base URL**
  - [Open Application](https://stegnography-1-jo07.onrender.com/swagger-ui/index.html)

---

## 🚀 Live API Access

* 🔐 **Encode API** *(POST only)*
  ``` https://stegnography-1-jo07.onrender.com/encode```

* 🔓 **Decode API** *(POST only)*
  ```https://stegnography-1-jo07.onrender.com/decode```

> ⚠️ Note: These are POST APIs and cannot be used directly in a browser. Use Postman or curl.

---

## 📘 Swagger UI (API Documentation)

- [Open Swagger UI](https://stegnography-1-jo07.onrender.com/swagger-ui/index.html)

- [View API Docs (JSON)](https://stegnography-1-jo07.onrender.com/v3/api-docs)

> ⚠️ Swagger works only if enabled in the deployed backend.

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

---

## LSB Technique (Least Significant Bit)

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

## Running the Application (Local)

**Prerequisites:** Java 21, Maven 3.8+

```bash
mvn clean package
java -jar target/steganography-demo-1.0.0.jar
```

Local Server:
http://localhost:8080

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

## Postman Usage (Recommended)

### Encode

* Method: POST

* URL:
  https://stegnography-1-jo07.onrender.com/encode

* Body → form-data:

| KEY      | TYPE | VALUE               |
| -------- | ---- | ------------------- |
| image    | File | input.png           |
| message  | Text | Meet me at midnight |
| password | Text | secret123           |

👉 Save response as `stego_output.png`

---

### Decode

* Method: POST

* URL:
  https://stegnography-1-jo07.onrender.com/decode

* Body → form-data:

| KEY      | TYPE | VALUE            |
| -------- | ---- | ---------------- |
| image    | File | stego_output.png |
| password | Text | secret123        |

👉 Response: `Meet me at midnight`

---

## Curl Usage

```bash
curl -X POST https://stegnography-1-jo07.onrender.com/encode \
  -F "image=@input.png" \
  -F "message=Hello" \
  -F "password=secret" \
  --output stego.png
```

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
* No frontend (tested via Postman)

---

## Tech Stack

* Java 21
* Spring Boot
* Maven

---

*Built for: CNS / CSE — ANITS*
