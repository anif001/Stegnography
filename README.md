# Steganography Demo — Spring Boot

**LSB Steganography + AES-256 Encryption**  
Hides secret messages inside PNG images. No database. No frontend. Pure REST APIs.

---

## How It Works

```
ENCODE:
  plaintext message
      │
      ▼  AES-256 (PBKDF2 key from password)
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
      ▼  AES-256 decryption (same password)
  original plaintext message
```

### LSB Technique (Least Significant Bit)
Each pixel has R, G, B channels (0–255 each). The last bit (LSB) of a channel
value changes the colour by only ±1 — completely invisible to the human eye.

We replace the LSB of R, G, B for each pixel with one bit of our secret data:
- 3 bits per pixel
- First 32 bits = payload length header (4-byte int)
- Remaining bits = AES-encrypted message bytes

---

## Project Structure

```
src/main/java/com/stego/
├── SteganographyApplication.java     ← Spring Boot entry point
├── controller/
│   └── StegoController.java          ← REST endpoints (/encode, /decode)
├── service/
│   └── StegoService.java             ← Orchestration logic
└── util/
    ├── CryptoUtil.java               ← AES-256 encrypt/decrypt (PBKDF2 key)
    └── SteganographyUtil.java        ← LSB encode/decode on BufferedImage
```

---

## Running the Application

**Prerequisites:** Java 21, Maven 3.8+

```bash
# 1. Clone / download the project
cd steganography-demo

# 2. Build
mvn clean package

# 3. Run
java -jar target/steganography-demo-1.0.0.jar

# Server starts at: http://localhost:8080
```

---

## API Endpoints

### POST /encode

Hides an encrypted message inside a PNG image.

| Field    | Type | Description                  |
|----------|------|------------------------------|
| image    | File | Carrier PNG image            |
| message  | Text | The secret message to hide   |
| password | Text | Password for AES encryption  |

**Response:** PNG image (binary). Save it — this is your stego-image.

---

### POST /decode

Extracts and decrypts a hidden message from a stego-image.

| Field    | Type | Description                                  |
|----------|------|----------------------------------------------|
| image    | File | The stego PNG from /encode                   |
| password | Text | Same password used during encoding           |

**Response:** Plain text — the original secret message.

---

## Postman Instructions

### Step 1 — Encode (hide a message)

1. Open Postman → New Request
2. Method: **POST**
3. URL: `http://localhost:8080/encode`
4. Go to **Body** tab → select **form-data**
5. Add three rows:

   | KEY      | TYPE | VALUE                          |
   |----------|------|--------------------------------|
   | image    | File | *(click "Select Files" → pick any .png)* |
   | message  | Text | `Meet me at midnight`          |
   | password | Text | `mySecret123`                  |

6. Click **Send**
7. In the response panel → click **"Save Response"** → **"Save to a file"**
8. Save as `stego_output.png`

---

### Step 2 — Decode (extract the message)

1. New Request → Method: **POST**
2. URL: `http://localhost:8080/decode`
3. **Body** → **form-data**

   | KEY      | TYPE | VALUE                                |
   |----------|------|--------------------------------------|
   | image    | File | *(select `stego_output.png` from step 1)* |
   | password | Text | `mySecret123`                        |

4. Click **Send**
5. Response body: `Meet me at midnight` ✓

---

### Step 3 — Test Wrong Password

Same as Step 2 but use a different password (e.g., `wrongPass`).

**Expected response (400):**
```
Decode Error: Wrong password or corrupted data. Please check your password and try again.
```

---

## Image Size Requirements

The carrier image must be large enough to hold the message.

| Image Size | Max Message (approx.) |
|------------|----------------------|
| 100×100    | ~3 700 bytes         |
| 512×512    | ~98 000 bytes        |
| 1920×1080  | ~777 000 bytes       |

If the image is too small, the API returns:
```
Encode Error: Image too small! Can hold X bytes but payload is Y bytes.
```

---

## Security Notes (Exam Context)

| Feature          | Implementation                          |
|------------------|-----------------------------------------|
| Encryption algo  | AES-256 (CBC mode, PKCS5 padding)       |
| Key derivation   | PBKDF2WithHmacSHA256, 65 536 iterations |
| Steganography    | LSB — 1 bit per RGB channel per pixel   |
| Wrong password   | BadPaddingException → 400 response      |
| Image format     | PNG only (JPEG lossy compression destroys LSB data!) |

---

*Built for: Renewable Energy Technologies / CSE Elective — ANITS*
