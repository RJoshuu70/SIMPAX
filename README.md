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
- [ ] Tahap 1 — JHipster app skeleton (entities: Role, User, BudgetsTransaction)
- [ ] Tahap 2 — Docker Compose dasar (Backend + PostgreSQL)
- [ ] Tahap 3 — Integrasi Apache APISIX + etcd
- [ ] Tahap 4 — Integrasi Apache Syncope (IAM)
- [ ] Tahap 5 — Integrasi Apache Fortress (RBAC) *atau fallback OPA/Keycloak*
- [ ] Tahap 6 — Modul bisnis (Kalkulator Pajak, Kwitansi & Faktur)
- [ ] Tahap 7 — Dashboard, Audit Trail UI, Landing Page
- [ ] Tahap 8 — Laporan akademik final

## 5. Catatan Risiko Penting

> ⚠️ **Status Apache Fortress** sebagai komponen RBAC inti perlu divalidasi ulang sebelum implementasi penuh — terdapat indikasi proyek ini sudah dipindahkan ke Apache Attic (retired status) dan tidak lagi menerima security patch resmi. Untuk konteks PoC akademik, Fortress tetap relevan sebagai model konseptual ANSI RBAC (ANSI INCITS 359). Apabila proses build/integrasi gagal pada deadline yang ditentukan, alternatif yang dipertimbangkan adalah **Keycloak Authorization Services**, **Casbin**, atau **Open Policy Agent (OPA)**. Lihat Bab 14.1 PRD untuk detail mitigasi.

## 6. Cara Menjalankan (akan diperbarui setiap tahap)

Instruksi setup lengkap akan ditambahkan secara progresif di bagian ini seiring setiap tahap selesai diimplementasikan dan diverifikasi.

---

*Repository ini dikelola sebagai bagian dari simulasi akademik untuk Mata Kuliah Keamanan Jaringan.*
