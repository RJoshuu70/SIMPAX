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
- **Dimensi Keamanan** — arsitektur *defense-in-depth* berbasis Zero Trust Architecture (NIST SP 800-207), dengan kontrol yang secara eksplisit dipetakan terhadap OSI Layer 5, 6, dan 7.

Dokumen PRD lengkap tersedia di [`docs/prd/PRD_SIMPAX_v1_0.md`](docs/prd/PRD_SIMPAX_v1_0.md).

## 2. Tech Stack

| Layer | Teknologi |
|---|---|
| Frontend | React (JHipster) |
| Backend | Spring Boot (JHipster) |
| API Gateway | Apache APISIX |
| Gateway Config Store | etcd |
| Identity Management | Apache Syncope |
| Authorization Engine (RBAC) | Apache Fortress *(lihat catatan risiko di bawah)* |
| Database | PostgreSQL |
| Containerization | Docker & Docker Compose |

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
- [~] Tahap 1 — Backend skeleton (entity, repository, JWT RS256) — **sedang berjalan**
- [ ] Tahap 2 — Docker Compose dasar (Backend + PostgreSQL)
- [ ] Tahap 3 — Integrasi Apache APISIX + etcd *(domain: Zaskia)*
- [ ] Tahap 4 — Integrasi Apache Syncope (IAM) *(domain: Zaskia)*
- [ ] Tahap 5 — Integrasi Apache Fortress (RBAC) — **cukup demonstrasi konsep** *(domain: Zaskia)*
- [ ] Tahap 6 — Modul bisnis (Kalkulator Pajak, Kwitansi & Faktur, Saldo & Saham)
- [ ] Tahap 7 — Dashboard, Audit Trail UI, Landing Page
- [ ] Tahap 8 — Laporan akademik final

> **Pembagian tugas tim:** Joshua (Backend Dev) — Zaskia (Security & Config Engineer) — Sekar (Security Tester) — Regina (Monitoring, Deployment & Dokumentasi). Detail di [`docs/diagrams/pembagian_tugas.md`](docs/diagrams/pembagian_tugas.md).

## 5. Catatan Risiko Penting

> ⚠️ **Status Apache Fortress** — disepakati tim: cukup didemonstrasikan secara konseptual (PoC terbatas), tidak perlu integrasi penuh production-grade, karena indikasi proyek ini sudah masuk Apache Attic.

## 6. Cara Menjalankan — Tahap 1 (Backend saja, tanpa Docker)

Prasyarat di laptop Anda:
- JDK 21
- Maven 3.9+ (atau gunakan `./mvnw` jika sudah ada wrapper)
- PostgreSQL 15+ berjalan lokal (atau lewat Docker, lihat Tahap 2 nanti)

Langkah:

```bash
# 1. Generate keypair RSA untuk JWT (sekali saja)
chmod +x scripts/generate-jwt-keypair.sh
./scripts/generate-jwt-keypair.sh

# 2. Buat database PostgreSQL kosong
#    (sesuaikan nama db/user/password dengan .env Anda)
createdb simpax

# 3. Copy .env.example menjadi .env lalu isi kredensial
cp .env.example .env

# 4. Masuk ke folder backend
cd simpax-app

# 5. Build & jalankan (set env var sesuai .env Anda, atau export manual)
export POSTGRES_DB=simpax
export POSTGRES_USER=simpax_admin
export POSTGRES_PASSWORD=<password_anda>

mvn clean install
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Jika berhasil, aplikasi akan otomatis menjalankan migrasi Liquibase
(membuat seluruh tabel + seed data 4 role) dan backend dapat diakses di
`http://localhost:8080`.

**Jika ada error saat `mvn clean install` atau saat start aplikasi, mohon
kirimkan pesan error LENGKAP (jangan dipotong) agar dapat didiagnosa
dengan tepat.**



---

*Repository ini dikelola sebagai bagian dari simulasi akademik untuk Mata Kuliah Keamanan Jaringan.*
