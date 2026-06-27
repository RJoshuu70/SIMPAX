# Panduan Pengujian — Apache Stack SIMPAX (APISIX, OpenLDAP/Fortress RBAC, Syncope)

**Untuk:** Sekar (Security Tester)
**Status implementasi saat ini (lihat README §4):**
- ✅ Apache APISIX + etcd — gateway & JWT verification
- ✅ Apache Fortress RBAC (via OpenLDAP, sebagai pengganti fortress-core) — otorisasi granular
- ⚠️ Apache Syncope — container sudah berjalan, **belum** terhubung ke alur autentikasi utama (lihat catatan di bagian 4)

Dokumen ini berisi test case yang bisa langsung dieksekusi dengan `curl`. Untuk setiap test
case: tujuan, langkah, dan kriteria lulus (expected result).

---

## 0. Prasyarat sebelum mulai testing

```bash
# Clone & masuk ke repo, lalu jalankan stack lengkap
docker compose up -d --build
docker compose ps    # semua service harus "Up" (postgres healthy, backend healthy)

# Load role RBAC ke OpenLDAP (sekali saja, kecuali volume di-reset)
docker cp infra/fortress/fortress-rbac.ldif simpax-openldap:/tmp/fortress-rbac.ldif
docker exec simpax-openldap ldapadd -x \
  -D "cn=admin,dc=simpax,dc=local" -w "$LDAP_ADMIN_PASSWORD" \
  -f /tmp/fortress-rbac.ldif

# Sinkronkan public key JWT backend ke APISIX sebagai Consumer
pip install requests cryptography --break-system-packages
python3 sync_jwks_to_apisix.py \
  --jwks-url http://localhost:8080/.well-known/jwks.json \
  --apisix-admin-url http://localhost:9180 \
  --apisix-admin-key "$APISIX_ADMIN_KEY" \
  --consumer-key simpax-backend
```

Variabel yang dipakai di seluruh contoh di bawah:

```bash
export APISIX_HTTP=http://localhost:9080      # data plane (lalu lintas request)
export APISIX_ADMIN=http://localhost:9180     # control plane (konfigurasi)
export BACKEND=http://localhost:8080
```

> **Catatan penting:** route APISIX ke backend (path `/api/...` dengan plugin `jwt-auth`,
> `key_claim_name: iss`) belum terlihat dikonfigurasi otomatis oleh script manapun di repo.
> Sebelum lanjut ke Bagian 1 dan 3, konfirmasikan ke Zaskia apakah route tersebut sudah
> dibuat manual via Admin API. Jika belum, test case 1.2–1.4 dan Bagian 3 akan gagal bukan
> karena bug, tapi karena route memang belum ada — catat sebagai temuan, jangan langsung
> tandai "FAIL".

---

## 1. Apache APISIX — Gateway & JWT Verification

### 1.1 Admin API tidak boleh diakses dari luar subnet yang diizinkan
**Tujuan:** memastikan `allow_admin` di `config.yaml` benar-benar membatasi akses.
```bash
curl -i "$APISIX_ADMIN/apisix/admin/routes" \
  -H "X-API-KEY: $APISIX_ADMIN_KEY"
```
- **Lulus jika:** dari host lokal (termasuk dalam `127.0.0.1/32`) request berhasil (200).
  Jika diuji dari luar Docker network/host (mis. dari mesin lain di jaringan yang sama),
  harus ditolak (connection refused/forbidden), karena `allow_admin` hanya mencakup
  `127.0.0.1/32` dan `172.16.0.0/12`.

### 1.2 Request tanpa token JWT ke route yang dilindungi harus ditolak
```bash
curl -i "$APISIX_HTTP/api/transactions/mine"
```
- **Lulus jika:** APISIX mengembalikan `401 Unauthorized` (ditolak oleh plugin `jwt-auth`
  sebelum sempat sampai ke backend).

### 1.3 Request dengan token JWT valid berhasil diteruskan ke backend
```bash
# Dapatkan token dulu (lihat Bagian 3.1 untuk cara login)
TOKEN="<isi dengan access token hasil login>"
curl -i "$APISIX_HTTP/api/transactions/mine" \
  -H "Authorization: Bearer $TOKEN"
```
- **Lulus jika:** status bukan 401 dari APISIX (artinya signature RS256 berhasil
  diverifikasi memakai public key yang disinkronkan `sync_jwks_to_apisix.py`), dan respons
  yang diterima konsisten dengan respons backend langsung (lihat 3.x).

### 1.4 Token dengan signature tidak valid (dipalsukan) harus ditolak
```bash
FAKE_TOKEN="eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhdHRhY2tlciJ9.invalidsignature"
curl -i "$APISIX_HTTP/api/transactions/mine" \
  -H "Authorization: Bearer $FAKE_TOKEN"
```
- **Lulus jika:** `401 Unauthorized`, request tidak pernah mencapai backend (cek log
  `docker compose logs backend` — tidak boleh ada entry untuk request ini).

### 1.5 Token kadaluwarsa (expired) harus ditolak
- Generate token, tunggu lebih dari `JWT_EXPIRY_MINUTES` (default 15 menit), lalu pakai
  ulang ke endpoint yang sama.
- **Lulus jika:** `401 Unauthorized`.

### 1.6 etcd tidak boleh terekspos tanpa kontrol ke publik
```bash
curl -i http://localhost:2379/version
```
- **Catat sebagai temuan** (bukan langsung fail/pass) jika port etcd `2379` ternyata
  dapat diakses dari luar host — di PoC akademik ini diterima untuk kemudahan
  development, tapi wajib dicatat sebagai risiko di laporan (etcd tidak punya auth
  di konfigurasi saat ini).

---

## 2. Apache Fortress RBAC (via OpenLDAP) — Otorisasi Granular

### 2.1 Verifikasi struktur role tersimpan benar di OpenLDAP
```bash
docker exec simpax-openldap ldapsearch -x \
  -D "cn=admin,dc=simpax,dc=local" -w "$LDAP_ADMIN_PASSWORD" \
  -b "ou=Roles,dc=simpax,dc=local" "(objectClass=groupOfNames)" cn description
```
- **Lulus jika:** muncul tepat 4 role — `STAFF`, `MANAGER`, `AUDITOR`, `DIREKSI` — sesuai
  RBAC matrix di PRD.

### 2.2 Matrix RBAC per role (uji lewat endpoint, bukan langsung ke LDAP)
Gunakan token JWT dengan claim `role` berbeda-beda (lihat Bagian 3 untuk cara mendapatkan
token per role), lalu panggil setiap endpoint berikut. Tabel ini adalah kriteria lulus:

| Endpoint | STAFF | MANAGER | AUDITOR | DIREKSI |
|---|---|---|---|---|
| `POST /api/transactions` (CREATE) | 201 | 201 | 403 | 403 |
| `POST /api/transactions/{id}/approve` | 403 | 200 | 403 | 403 |
| `POST /api/transactions/{id}/reject` | 403 | 200 | 403 | 403 |
| `GET /api/transactions/pending` (READ) | 200 | 200 | 200 | 200 |
| `GET /api/transactions/mine` (READ) | 200 | 200 | 200 | 200 |

Contoh perintah untuk satu baris (ganti `$TOKEN_STAFF`, dst.):
```bash
curl -i -X POST "$BACKEND/api/transactions" \
  -H "Authorization: Bearer $TOKEN_AUDITOR" \
  -H "Content-Type: application/json" \
  -d '{"description":"test","amount":100000}'
# Lulus jika: 403 Forbidden, body memuat "tidak boleh membuat transaksi"
```

### 2.3 Role yang tidak terdaftar di OpenLDAP harus selalu ditolak
- Buat/modifikasi token secara manual (di environment testing, bukan produksi) dengan
  claim `role` berisi nama role fiktif, misal `SUPERADMIN`.
- **Lulus jika:** semua endpoint mengembalikan `403`, dan log backend menunjukkan
  `"Role 'SUPERADMIN' tidak ditemukan di OpenLDAP, akses ditolak"`.

### 2.4 Privilege escalation — user STAFF tidak bisa approve transaksi miliknya sendiri
```bash
# 1. Login sebagai STAFF, buat transaksi, catat {id}
# 2. Coba approve transaksi tersebut memakai token STAFF yang sama
curl -i -X POST "$BACKEND/api/transactions/{id}/approve" \
  -H "Authorization: Bearer $TOKEN_STAFF"
```
- **Lulus jika:** `403 Forbidden`. Ini adalah test case keamanan paling kritis dari modul
  approval — staff tidak boleh bisa menyetujui transaksinya sendiri sekalipun secara teknis
  tahu ID transaksinya.

### 2.5 OpenLDAP down → fail-secure, bukan fail-open
```bash
docker compose stop openldap
curl -i "$BACKEND/api/transactions/mine" -H "Authorization: Bearer $TOKEN_STAFF"
docker compose start openldap   # jangan lupa nyalakan kembali setelah test
```
- **Lulus jika:** request ditolak (403/500), **bukan** diizinkan begitu saja. `roleExists()`
  di `FortressRbacService` menangkap exception dan mengembalikan `false`, sehingga
  `checkAccess()` juga `false` — perilaku yang diharapkan adalah fail-secure. Catat hasil
  aktualnya di laporan.

---

## 3. End-to-End: Login → JWT → Gateway → RBAC

### 3.1 Login dan dapatkan token
```bash
curl -s -X POST "$BACKEND/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"<username_staff_seed>","password":"<password>"}' | tee login_response.json
```
- **Lulus jika:** respons berisi access token JWT yang valid secara struktur (3 bagian
  dipisah titik, header `alg: RS256`).
- Decode payload token (tanpa verifikasi signature, hanya untuk cek isi):
  ```bash
  echo "<bagian_payload_token>" | base64 -d
  ```
  Pastikan claim `role`, `iss` (= `simpax-backend`), dan `key` (= `simpax-backend`) ada —
  claim `key` inilah yang dipakai APISIX untuk mencocokkan Consumer.

### 3.2 Validasi public key JWKS konsisten dengan yang disinkronkan ke APISIX
```bash
curl -s "$BACKEND/.well-known/jwks.json" | tee jwks_backend.json

curl -s "$APISIX_ADMIN/apisix/admin/consumers/simpax-backend" \
  -H "X-API-KEY: $APISIX_ADMIN_KEY" | tee consumer_apisix.json
```
- **Lulus jika:** modulus (`n`) pada JWKS backend identik dengan public key yang tersimpan
  pada konfigurasi plugin `jwt-auth` consumer APISIX.

### 3.3 Alur penuh: login (backend) → akses resource (lewat APISIX) → RBAC check (OpenLDAP)
```bash
TOKEN=$(curl -s -X POST "$BACKEND/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"<username_manager_seed>","password":"<password>"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

curl -i -X POST "$APISIX_HTTP/api/transactions/{id}/approve" \
  -H "Authorization: Bearer $TOKEN"
```
- **Lulus jika:** request berhasil melewati ketiga lapis (APISIX verifikasi JWT → Spring
  Security ekstrak role dari token → FortressRbacService cek role `MANAGER` ke OpenLDAP)
  dan mengembalikan `200 OK` dengan status transaksi berubah jadi approved.

---

## 4. Catatan untuk Sekar Sebelum Mulai

1. **Apache Syncope belum tersambung ke alur login.** Saat ini `AuthController` memvalidasi
   kredensial langsung di backend (lihat `SyncopeIdentityProviderClient`, perlu dicek apakah
   sudah memanggil Syncope atau masih placeholder). Konfirmasi dulu ke Joshua/Zaskia sebelum
   menulis test case untuk Syncope — jangan asumsikan endpoint Syncope (`:8081`) sudah
   menjadi sumber identitas yang sebenarnya dipakai.
2. Semua nilai `<...>` di atas (username, password, id transaksi) perlu diisi dari data seed
   yang dipakai tim development — minta ke Joshua kredensial user dummy per role.
3. Jika ada test case yang gagal **karena fitur/route belum ada** (bukan karena bug),
   pisahkan kategorinya dari kegagalan fungsional di laporan akhir — keduanya penting tapi
   punya implikasi berbeda untuk laporan Tugas Akhir.
4. Simpan setiap request/response (curl `-i` outputnya) sebagai bukti — laporan akademik
   akan butuh evidence, tidak cukup hanya "PASS/FAIL".
