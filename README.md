# SIMPAX — Sistem Informasi Manajemen Pajak dan Keuangan

> Arsitektur Keamanan pada OSI Layer 5 (Session), Layer 6 (Presentation), dan Layer 7 (Application)
> untuk Platform SaaS Keuangan Berbasis Zero Trust Architecture

**Konteks Akademik:** Tugas Akhir (UAS) Mata Kuliah Keamanan Jaringan
**Program Studi:** Sistem Informasi — Universitas Pembangunan Nasional "Veteran" Jakarta
**Status:** 🚧 Work in Progress (PoC)

---

## 1. Ringkasan

SIMPAX adalah simulasi produk SaaS yang mengintegrasikan dua dimensi secara setara:

- **Dimensi Bisnis** — otomasi pembukuan, kalkulasi PPN/PPh, manajemen kwitansi & faktur.
- **Dimensi Keamanan** — arsitektur _defense-in-depth_ berbasis Zero Trust Architecture (NIST SP 800-207), dengan kontrol yang secara eksplisit dipetakan terhadap OSI Layer 5, 6, dan 7.

Dokumen PRD lengkap tersedia di [`docs/prd/PRD_SIMPAX_v1_0.md`](docs/prd/PRD_SIMPAX_v1_0.md).

## 2. Tech Stack

| Layer                       | Teknologi                                         |
| --------------------------- | ------------------------------------------------- |
| Frontend                    | React (JHipster)                                  |
| Backend                     | Spring Boot (JHipster)                            |
| API Gateway                 | Apache APISIX                                     |
| Gateway Config Store        | etcd                                              |
| Identity Management         | Apache Syncope                                    |
| Authorization Engine (RBAC) | Apache Fortress _(lihat catatan risiko di bawah)_ |
| Database                    | PostgreSQL                                        |
| Containerization            | Docker & Docker Compose                           |

## 3. Struktur Repository

```
simpax/
├── simpax-app/          # Aplikasi JHipster (Spring Boot + React)
├── infra/                # Konfigurasi seluruh security stack
│   ├── apisix/            # Route, plugin (rate-limit, jwt-auth, ip-restriction)
│   ├── syncope/           # Konfigurasi Identity Management
│   ├── fortress/          # Konfigurasi RBAC engine
│   └── postgres/init/     # Skrip inisialisasi skema DB
├── docs/
│   ├── prd/               # Product Requirements Document
│   ├── laporan/           # Laporan akademik UAS
│   └── diagrams/          # Architecture & sequence diagrams
├── scripts/               # Helper scripts (health-check, seed data, dll)
├── docker-compose.yml
└── .env.example
```

## 4. Status Pengerjaan

- [x] Tahap 0 — Struktur repo & skeleton dasar
- [x] Tahap 1 — Backend skeleton (entity, repository, JWT RS256) ✅ **Terverifikasi: build sukses, 30 file Java compile tanpa error**
- [x] Tahap 2 — Docker Compose (Backend + PostgreSQL) ✅ **Terverifikasi: container Up & Healthy, Liquibase migrate sukses (7 changeset), actuator health UP, JWKS endpoint mengembalikan public key valid**
- [x] Tahap 3 — Integrasi Apache APISIX + etcd _(domain: Zaskia)_ ✅ **Terverifikasi manual (curl): route publik (`/api/auth/*`, JWKS) dan route terlindungi (`/api/transactions`, `/api/transactions/*`) aktif; plugin `jwt-auth` (key_claim_name "iss") menolak request tanpa token (401) dan meneruskan token valid; plugin `limit-count` aktif (header `X-RateLimit-*` muncul di response). Catatan: TLS/HTTPS di APISIX BELUM dikonfigurasi (lihat §5) - saat ini masih HTTP biasa.**
- [x] Tahap 4 — Integrasi Apache Syncope (IAM) _(domain: Zaskia)_ ✅ **Terverifikasi end-to-end: register user baru benar-benar memanggil Syncope REST API (`/syncope/rest/users`, payload `UserCR` dikonfirmasi cocok dengan skema resmi di `/syncope/rest/openapi.json`) dan login memvalidasi password via Syncope sebelum JWT RS256 diterbitkan.**
- [x] Tahap 5 — Integrasi Apache Fortress (RBAC) — **cukup demonstrasi konsep** _(domain: Zaskia)_ ✅ **Terverifikasi: RBAC matrix (STAFF/MANAGER/AUDITOR/DIREKSI) di `FortressRbacService` diuji 3 skenario - tanpa token (401 dari APISIX), role tidak memenuhi rule Spring Security `hasAnyRole` (403 kosong), dan role gagal di `checkAccess()` murni (403 dengan pesan spesifik "Akses ditolak: role AUDITOR tidak boleh membuat transaksi"). Implementasi memakai OpenLDAP + Spring LDAP, BUKAN library fortress-core asli (tidak kompatibel Spring Boot 3.x) - lihat §5.**
- [ ] Tahap 6 — Modul bisnis (Kalkulator Pajak, Kwitansi & Faktur, Saldo & Saham)
- [ ] Tahap 7 — Dashboard, Audit Trail UI, Landing Page
- [ ] Tahap 8 — Laporan akademik final

> **Catatan jujur soal kelengkapan Tahap 3-5:** inti fungsional (routing, autentikasi, otorisasi bertingkat) sudah terbukti jalan lewat pengujian manual end-to-end. Yang BELUM ada: (1) automated test (unit/integration test Java) - seluruh pembuktian di atas dilakukan manual via `curl`, belum ada yang bisa dijalankan otomatis lewat `mvn test`; (2) TLS/HTTPS pada APISIX, yang disebut sebagai tanggung jawab Layer 6 di komentar `SecurityConfig.java` tapi belum sempat dikonfigurasi.

> **Pembagian tugas tim:** Joshua (Backend Dev) — Zaskia (Security & Config Engineer) — Sekar (Security Tester) — Regina (Monitoring, Deployment & Dokumentasi). Detail di [`docs/diagrams/pembagian_tugas.md`](docs/diagrams/pembagian_tugas.md).

## 5. Catatan Risiko Penting

> ⚠️ **Status Apache Fortress** — disepakati tim: cukup didemonstrasikan secara konseptual (PoC terbatas), tidak perlu integrasi penuh production-grade, karena indikasi proyek ini sudah masuk Apache Attic.

## 6. Cara Menjalankan — Tahap 1 & 2 (Backend + PostgreSQL via Docker)

**Status: terverifikasi berhasil di environment Windows + Docker Desktop.**

Prasyarat di laptop Anda:

- Docker Desktop (sudah berjalan)
- Git Bash (untuk menjalankan shell script)
- OpenSSL (biasanya sudah bundled bersama Git for Windows)

Langkah:

```bash
# 1. Generate keypair RSA untuk JWT (sekali saja)
chmod +x scripts/generate-jwt-keypair.sh
./scripts/generate-jwt-keypair.sh

# 2. Copy .env.example menjadi .env lalu isi POSTGRES_PASSWORD
cp .env.example .env
# edit .env, isi POSTGRES_PASSWORD dengan password pilihan Anda

# 3. Build & jalankan seluruh stack (Postgres + Backend)
docker compose up -d --build

# 4. Pantau log sampai muncul "Started SimpaxApp in X seconds"
docker compose logs -f backend
```

### Verifikasi cepat

```bash
docker compose ps                                   # pastikan kedua container "Up"/"Healthy"
curl http://localhost:8080/actuator/health           # harus: {"status":"UP"}
curl http://localhost:8080/.well-known/jwks.json     # harus mengembalikan JSON berisi public key RSA
```

### Menghentikan stack

```bash
docker compose down       # stop & hapus container, DATA tetap aman di volume
docker compose down -v    # stop & HAPUS volume database juga (hati-hati, data hilang)
```

**Catatan untuk Zaskia:** endpoint `http://localhost:8080/.well-known/jwks.json` (atau
`http://backend:8080/...` dari dalam network Docker yang sama) mengekspos public key RSA
yang dapat dikonfigurasikan langsung pada plugin `jwt-auth` Apache APISIX untuk verifikasi
token tanpa perlu copy-paste manual.

---

_Repository ini dikelola sebagai bagian dari simulasi akademik untuk Mata Kuliah Keamanan Jaringan._
