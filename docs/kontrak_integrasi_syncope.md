# Kontrak Integrasi: Backend (Joshua) ↔ Apache Syncope (Zaskia)

> Dokumen ini berisi asumsi teknis yang SUDAH ditulis di kode backend
> (`SyncopeIdentityProviderClient.java`). Tujuannya: Zaskia tinggal
> konfirmasi cocok/tidak dengan setup Syncope yang sedang dikerjakan,
> bukan mulai diskusi dari nol.

## 1. Endpoint yang backend akan panggil

### a) Validasi kredensial saat login

```
POST {SYNCOPE_BASE_URL}/syncope/rest/accessTokens/login
Header: Authorization: Basic base64(username:password)
```

- Response **200 OK** → kredensial valid, backend lanjut generate JWT sendiri
- Response **401 Unauthorized** → kredensial tidak valid, backend tolak login

**Pertanyaan untuk Zaskia:**
- [ ] Apakah path endpoint ini sesuai versi Syncope yang dipakai? (asumsi: Syncope 3.x)
- [ ] Apakah Syncope perlu konfigurasi tambahan (CORS, dsb) agar bisa dipanggil dari backend Spring Boot?

### b) Registrasi user baru

```
POST {SYNCOPE_BASE_URL}/syncope/rest/users?storePassword=true
Header: Authorization: Basic base64(SYNCOPE_ADMIN_USER:SYNCOPE_ADMIN_PASSWORD)
Body:
{
  "@class": "org.apache.syncope.common.lib.to.UserTO",
  "username": "...",
  "password": "...",
  "plainAttrs": [
    { "schema": "email", "values": ["..."] }
  ]
}
```

- Response sukses **201 Created**, body berisi field `"key"` (UUID) → ini akan disimpan backend sebagai `syncope_ref_id`

**Pertanyaan untuk Zaskia:**
- [ ] Apakah skema atribut Syncope yang dipakai sudah punya `email` sebagai plain attribute? Atau ada nama schema lain yang harus dipakai?
- [ ] Apakah ada attribute lain WAJIB diisi saat create user (sesuai konfigurasi Syncope kita)?
- [ ] Bagaimana kredensial admin Syncope (`SYNCOPE_ADMIN_USER`/`PASSWORD`) akan dibagikan ke backend? (sebaiknya lewat `.env`, JANGAN hardcode/share di chat)

## 2. Konfigurasi yang backend butuhkan dari Zaskia

| Variable | Contoh nilai | Keterangan |
|---|---|---|
| `SYNCOPE_BASE_URL` | `http://syncope:8080` atau `http://localhost:8081` | Tergantung backend & Syncope di network Docker yang sama atau tidak |
| `SYNCOPE_ADMIN_USER` | `admin` | Username admin Syncope |
| `SYNCOPE_ADMIN_PASSWORD` | *(rahasia)* | Untuk operasi create user |

## 3. Yang backend SEDIAKAN untuk Zaskia (arah sebaliknya)

### JWKS Endpoint (public key untuk verifikasi JWT di APISIX)

```
GET http://backend:8080/.well-known/jwks.json
```

Sudah **terverifikasi jalan**, contoh response:
```json
{
  "keys": [{
    "kty": "RSA", "use": "sig", "alg": "RS256",
    "n": "...", "e": "AQAB"
  }]
}
```

Zaskia dapat konfigurasikan APISIX `jwt-auth` plugin untuk fetch public key dari URL ini secara otomatis (tidak perlu copy-paste manual file `.pem`).

### Format Claim JWT yang backend terbitkan

```json
{
  "sub": "username",
  "userId": "uuid-user",
  "role": "STAFF | MANAGER | AUDITOR | DIREKSI",
  "iss": "simpax-backend",
  "iat": ...,
  "exp": ...
}
```

**Pertanyaan untuk Zaskia:**
- [ ] Apakah APISIX butuh claim tambahan selain di atas untuk keperluan rate-limiting/RBAC di gateway?

## 4. Topologi Network (perlu disepakati bersama Regina juga)

Pertanyaan kunci: **apakah Syncope dan Backend akan berada di Docker Compose network yang SAMA**, atau terpisah?

- Jika **sama network**: backend bisa panggil `http://syncope:8080/...` (nama service Docker)
- Jika **terpisah/Syncope di luar Docker**: perlu `host.docker.internal` (Windows/Mac) atau IP statis

## 5. Rencana Fallback Jika Syncope Belum Siap

Agar progress modul lain (kalkulator pajak, kwitansi, saldo & saham) **tidak terhambat** menunggu Syncope:

- Backend sudah didesain dengan interface abstraksi `IdentityProviderClient`
- Bisa dibuat implementasi mock/stub sementara untuk testing lokal tanpa Syncope asli
- Begitu Syncope Zaskia ready, tinggal switch implementasi tanpa ubah kode pemanggil (`AuthenticationService`)

---
**Status dokumen:** Draft untuk diskusi - silakan dikomentari/diedit bersama Zaskia.
