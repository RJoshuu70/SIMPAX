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

> ⚠️ **Catatan penting soal kesesuaian dokumen vs kode:** PRD v1.0 awalnya mengasumsikan arsitektur monolith JHipster (backend + frontend React tergabung dalam satu aplikasi). Realita implementasi saat ini menunjukkan backend sudah berevolusi menjadi **pure REST API** (Spring Boot tanpa embedded frontend JHipster), dan **belum ada satu pun baris kode frontend** (tidak ada folder `webapp/`, tidak ada `package.json`, tidak ada stage frontend di Dockerfile). Tabel Tech Stack di bawah sudah disesuaikan dengan keputusan arsitektur terbaru (lihat §5.1).

## 2. Tech Stack

| Layer                       | Teknologi                                                                   |
| ---------------------------- | ---------------------------------------------------------------------------- |
| Frontend                    | **React + Vite (Standalone SPA)** — _direvisi dari rencana awal JHipster monolith, lihat §5.1_ |
| Backend                     | Spring Boot (skeleton awal dibuat via JHipster, kini berjalan sebagai pure REST API) |
| API Gateway                 | Apache APISIX                                                              |
| Gateway Config Store        | etcd                                                                       |
| Identity Management         | Apache Syncope _(container Up, belum terhubung ke alur auth utama — lihat §5.2)_ |
| Authorization Engine (RBAC) | OpenLDAP + Spring LDAP _(menggantikan Apache Fortress asli, lihat §5.2)_   |
| Database                    | PostgreSQL                                                                 |
| Containerization            | Docker & Docker Compose                                                    |

## 3. Struktur Repository

```
simpax/
├── simpax-app/          # Aplikasi backend (Spring Boot, skeleton awal dari JHipster)
├── infra/                # Konfigurasi seluruh security stack
│   ├── apisix/            # Route, plugin (rate-limit, jwt-auth, ip-restriction)
│   ├── syncope/           # Konfigurasi Identity Management
│   ├── fortress/          # LDIF struktur RBAC (STAFF/MANAGER/AUDITOR/DIREKSI) untuk OpenLDAP
│   └── postgres/init/     # Skrip inisialisasi skema DB (termasuk 01_create_syncope_db.sh)
├── docs/
│   ├── prd/               # Product Requirements Document
│   ├── laporan/           # Laporan akademik UAS
│   └── diagrams/          # erd_notes.md, kontrak_integrasi_syncope.md, sequence diagrams
├── scripts/               # Helper scripts (generate JWT keypair, setup APISIX routes)
├── sync_jwks_to_apisix.py # Sinkronisasi JWKS backend -> APISIX Consumer
├── TEST_GUIDE_APACHE_STACK.md  # Panduan pengujian (saat ini di root, bukan di docs/)
├── docker-compose.yml
└── .env.example
```

> Folder frontend (`simpax-webapp/` atau sejenisnya) **belum ada** dan akan ditambahkan pada Tahap 6 mengikuti keputusan arsitektur di §5.1.

## 4. Status Pengerjaan

- [x] Tahap 0 — Struktur repo & skeleton dasar
- [x] Tahap 1 — Backend skeleton (entity, repository, JWT RS256) ✅ **Terverifikasi: build sukses, 30 file Java compile tanpa error**
- [x] Tahap 2 — Docker Compose (Backend + PostgreSQL) ✅ **Terverifikasi: container Up & Healthy, Liquibase migrate sukses (7 changeset), actuator health UP, JWKS endpoint mengembalikan public key valid**
- [x] Tahap 3 — Integrasi Apache APISIX + etcd _(domain: Zaskia)_ ✅ **Terverifikasi: APISIX (traditional mode) + etcd Up, Admin API dibatasi ke subnet internal, route publik (`/api/auth/*`, JWKS) dan route terlindungi (`/api/transactions/**`) aktif, plugin `jwt-auth` (key_claim_name "iss") menolak request tanpa token (401) dan meneruskan token valid, plugin `limit-count` aktif (header `X-RateLimit-*` muncul di response), `sync_jwks_to_apisix.py` berhasil mendaftarkan Consumer `jwt-auth` dari JWKS backend.** Catatan: TLS/HTTPS di APISIX **belum** dikonfigurasi (lihat §6) — saat ini masih HTTP biasa.
- [x] Tahap 4 — Integrasi Apache Syncope (IAM) _(domain: Zaskia)_ ✅ **Terverifikasi end-to-end: register user baru benar-benar memanggil Syncope REST API (`/syncope/rest/users`, payload `UserCR` dikonfirmasi cocok dengan skema resmi di `/syncope/rest/openapi.json`) dan login memvalidasi password via Syncope sebelum JWT RS256 diterbitkan.**
- [x] Tahap 5 — RBAC via OpenLDAP + Spring LDAP _(pengganti Apache Fortress, domain: Zaskia)_ ✅ **Terverifikasi: struktur LDIF (Roles: STAFF/MANAGER/AUDITOR/DIREKSI) ter-load ke OpenLDAP; `FortressRbacService` melakukan RBAC check granular per-request di `BudgetsTransactionController`; diuji 3 skenario — tanpa token (401 dari APISIX), role tidak memenuhi rule Spring Security `hasAnyRole` (403 kosong), dan role gagal di `checkAccess()` murni (403 dengan pesan spesifik "Akses ditolak: role AUDITOR tidak boleh membuat transaksi").** Catatan: `fortress-core` asli tidak dipakai karena tidak kompatibel dengan Spring Boot 3.x — lihat §6.
- [x] Tahap 4b — Integrasi Apache Syncope penuh ke alur auth utama _(domain: Zaskia)_ ✅ **Terverifikasi independen (27 Juni 2026) — bukan lagi self-reported:** (1) `\l` di `psql` mengonfirmasi database `syncope` ada dengan owner `simpax_admin`, encoding `UTF8`, collate `en_US.utf8`; (2) log `simpax-syncope` menunjukkan startup bersih dua kali (`[Master] Empty database found, loading default content` di first-run, lalu `[Master] Data found in the database, leaving untouched` di restart kedua — membuktikan data persisten), tanpa satu pun stack trace error koneksi DB; (3) `POST /api/auth/register` mengembalikan `201 Created` dengan `userId` UUID (`6cca9dab-...`); (4) `POST /api/auth/login` dengan kredensial yang sama mengembalikan `200 OK` berisi JWT RS256 dengan claim `sub`, `userId` (cocok dengan UUID dari register), `role: STAFF`, `key: simpax-backend`, `iss` — sesuai format yang dijanjikan di [`kontrak_integrasi_syncope.md`](docs/diagrams/kontrak_integrasi_syncope.md). Rantai Backend → Syncope 4.1.1 → PostgreSQL `syncope` db terbukti nyambung end-to-end secara nyata, bukan asumsi dari kode.
- [ ] Tahap 6 — Modul bisnis (Kalkulator Pajak, Kwitansi & Faktur, Saldo & Saham) + scaffold Frontend (lihat §5.1 dan §5.3)
  - [x] Scaffold Frontend (`simpax-web/`, React + Vite SPA) ✅ **Compile belum diverifikasi independen (29 Juni 2026) — perlu `npm install && npm run dev` di mesin Joshua sebelum status ini dianggap final**, sesuai prinsip evidence-based yang dipakai sepanjang proyek ini. Mencakup: Login, Register, Dashboard (ringkasan saldo+portofolio dengan count-up animation), Dompet & Saham (create wallet, deposit, withdraw, buy/sell saham), Transaksi Budget (role-aware: tombol Setuju/Tolak hanya tampil untuk MANAGER/DIREKSI). Semua request HTTP terpusat lewat `src/services/api.js`, proxy ke APISIX port 9080 (bukan langsung ke backend 8080) sesuai prinsip Zero Trust. **Prasyarat infra:** route APISIX direvisi dari `/api/transactions/*` spesifik menjadi `/api/*` catch-all (lihat `scripts/setup_apisix_routes.sh`) supaya endpoint Wallet/Stock/modul masa depan otomatis terlindungi tanpa edit script berulang — **WAJIB re-run script ini sebelum testing dari UI**, kalau belum pernah dijalankan ulang sejak revisi ini.
  - [x] Saldo & Saham — Service + Controller (`WalletBalanceService/Controller`, `StockHoldingService/Controller`, `StockPriceCacheService`) ✅ **Terverifikasi end-to-end (29 Juni 2026), bukan cuma compile.** Eksekusi penuh [`scripts/test_wallet_stock_module.sh`](scripts/test_wallet_stock_module.sh) (11 skenario: 8 positif + 3 negatif) menunjukkan seluruh rantai create wallet → deposit → withdraw → buy saham → sell saham → ownership violation berfungsi benar, termasuk konsistensi transaksional lintas `WalletBalance` dan `StockHolding` (saldo & kepemilikan saham selalu sinkron setelah buy/sell). Satu bug nyata ditemukan dan diperbaiki selama proses verifikasi ini (bukan diasumsikan langsung benar): `AccessDeniedException` tertangkap oleh catch-all `@ExceptionHandler(Exception.class)` di `GlobalExceptionHandler` dan keliru dikembalikan sebagai `500` padahal seharusnya `403` — diperbaiki dengan menambah `@ExceptionHandler(AccessDeniedException.class)` eksplisit. `StockPriceCacheService` memakai mock provider deterministik (bukan API saham asli — lihat Javadoc kelas tersebut), perlu diganti sebelum klaim "harga real-time" dicantumkan di laporan.
  - [ ] Kalkulator Pajak (PPN/PPh)
  - [ ] Kwitansi & Faktur
- [ ] Tahap 7 — Dashboard, Audit Trail UI, Landing Page
- [ ] Tahap 8 — Laporan akademik final

> **Catatan jujur soal kelengkapan Tahap 3–5:** inti fungsional (routing, autentikasi, otorisasi bertingkat) sudah terbukti jalan lewat pengujian manual end-to-end. Yang **belum** ada: (1) automated test (unit/integration test Java) — seluruh pembuktian di atas dilakukan manual via `curl`, belum ada yang bisa dijalankan otomatis lewat `mvn test`; (2) TLS/HTTPS pada APISIX, yang disebut sebagai tanggung jawab Layer 6 di komentar `SecurityConfig.java` tapi belum sempat dikonfigurasi.

> **Pembagian tugas tim:** Joshua (Backend Dev) — Zaskia (Security & Config Engineer) — Sekar (Security Tester) — Regina (Monitoring, Deployment & Dokumentasi). _(Catatan: `docs/diagrams/pembagian_tugas.md` yang dirujuk di sini belum ditemukan di repo — perlu dibuat, atau link ini dihapus jika pembagian tugas hanya didokumentasikan inline seperti di atas.)_

> **Pengujian:** instruksi pengujian untuk 3 komponen Apache (APISIX, OpenLDAP/Fortress RBAC, dan integrasi end-to-end JWT → Gateway → RBAC) tersedia di [`TEST_GUIDE_APACHE_STACK.md`](TEST_GUIDE_APACHE_STACK.md) _(saat ini berada di root repo, bukan di `docs/pengujian/` — pertimbangkan dipindah ke `docs/pengujian/` agar konsisten dengan struktur folder `docs/` lainnya, atau biarkan di root dan sesuaikan saja struktur foldernya di §3)_.

## 5. Audit Kesesuaian: Code vs README vs PRD

Audit internal (per commit terakhir `master`, sinkron dengan `origin/RJoshuu70/SIMPAX`) menemukan beberapa gap antara dokumen perencanaan dan kondisi kode aktual. Berikut rangkuman temuan beserta status resolusinya, agar konsistensi dokumentasi terjaga sebagai bagian dari *compliance evidence* (relevan dengan COBIT 2019 domain **MEA04 — Managed Assurance**, yang menuntut bahwa artefak dokumentasi dapat ditelusuri balik ke kondisi sistem yang sebenarnya).

| Aspek | Klaim README/PRD (sebelumnya) | Realita di Code | Status Resolusi |
| --- | --- | --- | --- |
| Frontend | "React (JHipster)" | Tidak ada folder `webapp/`, tidak ada `package.json`, tidak ada stage frontend di Dockerfile/`docker-compose.yml` | 🔴 **Diputuskan:** dibangun ulang sebagai Standalone React + Vite SPA pada Tahap 6 (lihat §5.1) |
| Modul Pajak (PPN/PPh) | *Must Have*, Tahap 6 | Belum ada entity, tabel, service, maupun controller | Sesuai status `[ ]` — direncanakan Tahap 6 |
| Modul Kwitansi & Faktur | *Must Have*, Tahap 6 | Belum ada sama sekali, termasuk tabel Liquibase | Sesuai status `[ ]` — direncanakan Tahap 6 |
| Saldo & Saham | Tahap 6 | Entity `WalletBalance`, `StockHolding`, `StockPriceCache` sudah ada di domain + tabel Liquibase, **tapi belum ada Service maupun Controller** yang men-expose ke REST API | 🟡 Setengah jalan — perlu diselesaikan di Tahap 6 sebelum modul Pajak/Kwitansi (paling siap) |
| Audit Trail | "tersentralisasi", dicatat tiap ALLOW/DENY (PRD §6) | Belum ada tabel `audit_log` (atau sejenisnya) di Liquibase, belum ada service pencatat | Sesuai status `[ ]`, tapi desain skema harus dibuat lebih dulu sebelum Tahap 6 ditutup |
| Konvensi API path | PRD memakai `/api/v1/budgets/approve`, `/api/v1/receipts/**` | Implementasi aktual memakai `/api/auth/**`, `/api/transactions/**` (tanpa prefix `v1`) | 🟡 **PRD yang disesuaikan ke kode** — kode adalah *source of truth*; lihat §5.4 |

### 5.1 Keputusan Arsitektur Frontend

Karena belum ada satu pun baris kode frontend, dan backend secara de facto sudah menjadi pure REST API (bukan monolith JHipster dengan embedded React), ini adalah *architectural fork* yang signifikan dan perlu didokumentasikan secara eksplisit alih-alih dibiarkan sebagai inkonsistensi laten.

**Keputusan:** Frontend dibangun sebagai **Standalone React (Vite) SPA**, terpisah penuh dari backend, yang mengonsumsi REST API melalui Apache APISIX sebagai satu-satunya entry point (konsisten dengan prinsip Zero Trust — frontend tidak pernah memanggil backend secara langsung, semua request tetap melalui Gateway yang menerapkan `jwt-auth` dan `limit-count`).

Pertimbangan vs alternatif (untuk laporan akademik):

| Opsi | Kelebihan | Kekurangan |
| --- | --- | --- |
| **Standalone React + Vite SPA** *(dipilih)* | Build time jauh lebih cepat dibanding Webpack bawaan JHipster; dev server Vite mendukung HMR (Hot Module Replacement) yang lebih responsif; arsitektur frontend/backend terpisah bersih sehingga selaras dengan kondisi backend yang sudah menjadi pure REST API; lebih mudah dijelaskan sebagai bagian dari *API Gateway pattern* di laporan | Perlu setup ulang dari nol (routing, state management, HTTP client) karena tidak ada scaffold bawaan JHipster yang bisa dipakai langsung |
| Scaffold ulang JHipster monolith (React + Spring Boot tergabung) | Sesuai 1:1 dengan asumsi PRD awal, tidak perlu revisi dokumen | Bertentangan dengan kondisi backend saat ini yang sudah berjalan sebagai pure REST API tanpa Thymeleaf/embedded resource; effort rewrite backend lebih besar daripada effort membangun FE baru |
| Next.js (App Router) | SSR/SSG tersedia jika dibutuhkan SEO untuk landing page publik | Overhead tidak proporsional untuk aplikasi internal yang mayoritas halamannya berada di belakang autentikasi (dashboard, transaksi) — SSR tidak banyak memberi nilai tambah di sini |

Untuk modul backend yang belum ada sama sekali (Kalkulator Pajak, Kwitansi & Faktur), pendekatan yang disepakati adalah **backend-first**: entity → service → controller diselesaikan dan diuji terlebih dahulu (mengikuti pola yang sudah terbukti pada modul Transactions), baru kemudian frontend dibangun untuk mengonsumsinya. Urutan pengerjaan yang disarankan pada Tahap 6:

1. Selesaikan Service + Controller untuk `WalletBalance`/`StockHolding`/`StockPriceCache` (entity sudah ada, paling cepat selesai).
2. Rancang skema `audit_log` dan service pencatatannya (prasyarat governance sebelum modul transaksi baru ditambah).
3. Bangun modul Kalkulator Pajak (PPN/PPh) end-to-end.
4. Bangun modul Kwitansi & Faktur end-to-end.
5. Scaffold frontend Vite SPA, mulai dari halaman auth + dashboard ringkas, baru menyusul modul-modul di atas.

### 5.2 Status Apache Fortress & Apache Syncope

> ⚠️ Disepakati tim: Apache Fortress cukup didemonstrasikan secara konseptual (PoC terbatas), tidak perlu integrasi penuh production-grade, karena ada indikasi proyek ini sudah berstatus Apache Attic (retired). Implementasi aktual memakai OpenLDAP + Spring LDAP sebagai model konseptual ANSI RBAC, **bukan** library `fortress-core` asli (tidak kompatibel dengan Spring Boot 3.x). Jika proyek dilanjutkan ke tahap produksi nyata, alternatif yang lebih layak dipertimbangkan adalah Keycloak Authorization Services, Casbin, atau Open Policy Agent (OPA).

> Apache Syncope (versi **4.1.1**, bukan 3.x seperti asumsi awal kontrak integrasi) sudah dikonfirmasi oleh Zaskia (26 Juni 2026) tersambung penuh ke alur auth utama: login memvalidasi password via `POST /syncope/rest/accessTokens/login` sebelum JWT diterbitkan, dan registrasi user memanggil `POST /syncope/rest/users` dengan payload `UserCR` yang dicocokkan terhadap skema resmi `/syncope/rest/openapi.json`. Backend dan Syncope berada di Docker network yang sama (`simpax-network`) sehingga tidak memerlukan konfigurasi CORS tambahan.
>
> ✅ **Update 27 Juni 2026 — Status naik dari self-reported menjadi terverifikasi independen.** Setelah perbaikan lokasi `01_create_syncope_db.sh` dan penggantian image ke `postgres:16`, dilakukan pengujian end-to-end langsung: `psql -lqt` mengonfirmasi database `syncope` ada dan sehat; log `simpax-syncope` start bersih tanpa error koneksi DB; `POST /api/auth/register` (`201 Created`, `userId` UUID baru) dan `POST /api/auth/login` (`200 OK`, JWT RS256 dengan claim `userId` yang cocok dengan hasil register) keduanya berhasil dijalankan langsung dan hasilnya ditempel sebagai bukti — bukan lagi checklist tanpa artefak. Selisih versi Syncope (asumsi awal 3.x → realita 4.1.1) sudah terbukti tidak menjadi masalah untuk dua endpoint inti ini (login dan create user); endpoint Syncope lain yang belum pernah dipanggil tetap belum boleh diasumsikan otomatis kompatibel kalau suatu saat dibutuhkan (misal password policy atau schema management API).

### 5.3 Saran Penambahan Modul Audit Trail (Database Design)

Karena tabel `audit_log` belum ada, berikut usulan skema minimal yang konsisten dengan PRD §10 dan kebutuhan ISO/IEC 27001 Annex A (domain *Logging and Monitoring*):

| Kolom | Tipe | Catatan |
| --- | --- | --- |
| id | UUID / BIGSERIAL | Primary key |
| actor_username | VARCHAR | Subjek dari klaim JWT (`sub`) |
| actor_role | VARCHAR | Role aktif saat request dilakukan |
| action | VARCHAR | Misal `CREATE_TRANSACTION`, `APPROVE_TRANSACTION` |
| resource | VARCHAR | Endpoint atau entity yang diakses |
| decision | VARCHAR | `ALLOW` / `DENY` |
| reason | VARCHAR (nullable) | Pesan spesifik saat `DENY`, misal hasil `checkAccess()` |
| ip_address | VARCHAR | Untuk korelasi dengan log APISIX |
| created_at | TIMESTAMP | Waktu kejadian |

Skema ini sebaiknya dirampungkan sebelum modul bisnis baru ditambahkan di Tahap 6, supaya setiap entity baru (Kalkulator Pajak, Kwitansi & Faktur) langsung terintegrasi dengan pencatatan audit sejak awal, bukan ditambal belakangan.

### 5.4 Konvensi API Path

PRD v1.0 menulis contoh path dengan prefix `/api/v1/**`, sementara implementasi aktual memakai `/api/auth/**` dan `/api/transactions/**` tanpa versioning. Karena kode adalah *source of truth*, **PRD akan direvisi** pada versi berikutnya untuk mengikuti konvensi tanpa `v1`. Catatan untuk diskusi tim: pertimbangkan tetap menambahkan API versioning (`/api/v1/**`) sebelum Tahap 6 menambah banyak endpoint baru, karena migrasi path setelah banyak konsumen (frontend + dokumentasi pengujian) terbentuk akan jauh lebih mahal daripada menetapkannya sekarang.

## 6. Catatan Risiko & Item Terbuka

> **Keputusan Tim (28 Juni 2026) — ditunda secara sadar demi deadline UAS, bukan terlewat:**
> 1. **Bug `FortressRbacService` untuk role MANAGER** (kondisi `case "CREATE","READ","APPROVE" -> true` tidak memeriksa parameter `resource` sama sekali — ditemukan saat audit modul Saldo & Saham) **tidak diperbaiki sekarang**. Dampaknya terbatas pada modul `BudgetsTransaction` yang sudah ada (modul Saldo & Saham sengaja tidak memakai RBAC matrix ini sama sekali — lihat Javadoc `WalletBalanceService`), jadi risiko langsungnya rendah untuk fitur yang sedang dikerjakan. Tetap harus diperbaiki sebelum modul bisnis lain (Pajak, Kwitansi) menambah resource baru ke `checkAccess()`.
> 2. **Automated test (JUnit) untuk modul Saldo & Saham ditunda**, diganti sementara dengan skrip manual testing [`scripts/test_wallet_stock_module.sh`](scripts/test_wallet_stock_module.sh) yang mencakup 11 skenario (8 positif + 3 negatif, termasuk uji ownership violation antar user). Skrip ini **bukan pengganti permanen** automated test — hasil eksekusinya manual dan tidak terintegrasi ke CI — tapi cukup sebagai *compliance evidence* sementara untuk laporan akademik, lebih baik daripada tidak ada bukti sama sekali.

- **TLS/HTTPS pada APISIX** belum dikonfigurasi — saat ini seluruh komunikasi masih HTTP biasa di environment lokal. Ini disebut sebagai tanggung jawab Layer 6 di komentar `SecurityConfig.java` namun belum direalisasikan. Wajib diselesaikan sebelum klaim "Zero Trust" dapat dipertahankan secara penuh di laporan.
- **Automated test (Java) belum ada.** Seluruh pembuktian Tahap 3–5 dilakukan manual via `curl`; belum ada yang dapat dijalankan otomatis lewat `mvn test`. Disarankan minimal menambahkan integration test untuk skenario RBAC 3 kasus yang sudah diuji manual (401 tanpa token, 403 role tidak sesuai Spring Security, 403 dari `checkAccess()`), karena skenario ini sudah terdefinisi jelas dan murah untuk dikonversi menjadi test otomatis.
- **Apache Syncope** belum menjadi jalur auth utama secara penuh (lihat §5.2).
- ✅ **RESOLVED (27 Juni 2026) — Bug lokasi `01_create_syncope_db.sh`.** Sebelumnya skrip ini salah lokasi (`infra/postgres/01_create_syncope_db.sh`, satu level di luar `init/` yang di-mount Docker), sehingga tidak pernah dieksekusi. Setelah dipindah ke `infra/postgres/init/01_create_syncope_db.sh` dan dijalankan ulang via `docker compose down -v && up -d --build`, terverifikasi langsung lewat `docker exec simpax-postgres psql -U simpax_admin -lqt`: database `syncope` ada dengan owner `simpax_admin`, encoding `UTF8`.
- ✅ **RESOLVED (27 Juni 2026) — Bug locale `en_US.utf8` di image Alpine.** `docker-compose.yml` sudah diganti dari `postgres:16-alpine` ke `postgres:16` (Debian-based). Setelah perubahan ini, skrip `01_create_syncope_db.sh` berhasil jalan tanpa error locale — terkonfirmasi lewat output `psql -lqt` yang menunjukkan database `syncope` ber-collate `en_US.utf8` tanpa keluhan, dan log startup Syncope yang bersih (`Connected to PostgreSQL version 16.14`, tanpa stack trace error).
- **Version control hygiene:** repo sebelumnya menyisakan noise *line-ending* (CRLF vs LF) yang membuat `git diff` melaporkan puluhan file "modified" padahal isinya tidak berubah — kemungkinan karena konfigurasi `core.autocrlf` berbeda antar anggota tim (Windows vs macOS/Linux). Lihat §7 untuk langkah perbaikan.
- **Environment variable yang tidak konsisten/tidak terpakai (dead config):** `.env.example` mendefinisikan `FORTRESS_LDAP_PORT` dan `FORTRESS_ADMIN_PASSWORD`, tapi `docker-compose.yml` service `openldap` ternyata memakai nama variabel `LDAP_ADMIN_PASSWORD` (bukan `FORTRESS_ADMIN_PASSWORD`) dan port host yang di-hardcode `389:389` (tidak memakai `FORTRESS_LDAP_PORT` sama sekali). Demikian juga `SYNCOPE_HOST`/`SYNCOPE_PORT` yang dikirim sebagai environment variable ke container `backend` di `docker-compose.yml` ternyata **tidak terpakai** oleh profile `docker` (`application-docker.yml` meng-hardcode `simpax.syncope.base-url: http://syncope:8080` langsung, bukan menyusunnya dari `${SYNCOPE_HOST}`/`${SYNCOPE_PORT}`). Kebetulan nilai hardcode ini cocok dengan nilai env var-nya sehingga secara fungsional tidak error — tapi ini technical debt: kalau suatu saat `SYNCOPE_PORT` diubah lewat `.env`, perubahan itu TIDAK akan berefek di profile `docker`, hanya di profile `dev`. Sebaiknya disamakan: pakai placeholder env var di kedua profile, atau hardcode di kedua profile dengan catatan eksplisit kenapa.
- **Volume `simpax_syncope_data` dideklarasikan tapi tidak pernah dipakai** — koreksi atas catatan sebelumnya: service `syncope` memang tidak memiliki blok `volumes:` yang me-mount volume ini, **tapi ternyata bukan masalah nyata**. Log `simpax-syncope` (27 Juni 2026) membuktikan data tetap persisten antar restart container (`[Master] Data found in the database, leaving untouched` pada restart kedua) karena Syncope menyimpan seluruh state-nya di database `syncope` pada PostgreSQL (`simpax_postgres_data`), bukan di filesystem container Syncope sendiri. Volume `simpax_syncope_data` yang dideklarasikan tapi tidak terpakai ini cuma dead config yang sebaiknya dihapus dari `docker-compose.yml` agar tidak membingungkan pembaca — bukan bug fungsional.
- **`backend` depends_on `syncope` hanya `condition: service_started`, bukan `service_healthy`** — sementara service `syncope` di `docker-compose.yml` tidak punya `healthcheck` sama sekali. Apache Syncope dikenal lambat saat startup (bisa 1-3 menit tergantung resource). `service_started` cuma menjamin proses container sudah jalan, bukan aplikasi Syncope-nya sudah siap menerima request — jadi ada risiko race condition saat `backend` mencoba memanggil Syncope di percobaan pertama setelah `docker compose up`. Disarankan menambahkan `healthcheck` pada service `syncope` (misalnya curl ke endpoint actuator/health Syncope) dan mengganti kondisi `depends_on` backend menjadi `service_healthy`.
- **Link dokumentasi di README ini sendiri perlu dikoreksi** setelah saya cek struktur folder yang sebenarnya di zip: dokumen `kontrak_integrasi_syncope.md` ternyata berada di `docs/diagrams/kontrak_integrasi_syncope.md` (bukan `docs/kontrak_integrasi_syncope.md` seperti revisi saya sebelumnya), `TEST_GUIDE_APACHE_STACK.md` ternyata berada di **root repo**, bukan di `docs/pengujian/` (folder `docs/pengujian/` tidak ada sama sekali di zip), dan `docs/diagrams/pembagian_tugas.md` yang dirujuk di §4 README **tidak ditemukan** di zip — perlu dikonfirmasi apakah file ini memang belum dibuat atau terlewat saat zip dibuat. Saya sudah perbaiki path-path ini di bawah.

## 7. Version Control Hygiene

```
* text=auto
```

Lalu jalankan urutan berikut **sekali saja**, sebelum melanjutkan commit baru:

```bash
# 1. Pastikan .gitattributes sudah ada di root, lalu re-normalize working tree
git add --renormalize .

# 2. Cek ulang diff — seharusnya jadi kosong/minimal, bukan puluhan file
git diff --stat

# 3. Commit hanya perubahan yang REAL (README, file kosong yang sudah diputuskan, dst.)
git add README.md .gitattributes infra/postgres/01_create_syncope_db.sh
git commit -m "fix: bersihkan sisa merge conflict di README, tambah .gitattributes, lengkapi script syncope db"
git push origin master
```

> Poin ini juga layak dicantumkan di laporan akademik sebagai bagian dari kerapian *version control* — auditor (dosen) sering menilai kerapian repo sebagai indikator profesionalisme tim, terlepas dari isi kode itu sendiri.

## 8. Cara Menjalankan — Tahap 1 & 2 (Backend + PostgreSQL via Docker)

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

## 9. Cara Menjalankan — Tahap 3–5: APISIX + etcd + OpenLDAP (RBAC)

**Status: terverifikasi berhasil.** Stack penuh sekarang mencakup: PostgreSQL, Backend,
etcd, Apache APISIX, Apache Syncope (container, belum terhubung ke alur auth — lihat §5.2), dan OpenLDAP
(menyimpan struktur role untuk RBAC PoC).

Langkah tambahan setelah Tahap 1 & 2 di atas:

```bash
# 1. Jalankan seluruh stack
docker compose up -d --build

# 2. Pastikan semua container Up
docker compose ps

# 3. Load struktur RBAC (Roles: STAFF/MANAGER/AUDITOR/DIREKSI) ke OpenLDAP
docker cp infra/fortress/fortress-rbac.ldif simpax-openldap:/tmp/fortress-rbac.ldif
docker exec simpax-openldap ldapadd -x \
  -D "cn=admin,dc=simpax,dc=local" \
  -w "$LDAP_ADMIN_PASSWORD" \
  -f /tmp/fortress-rbac.ldif

# 4. Sinkronkan JWKS backend ke APISIX (mendaftarkan Consumer jwt-auth, RS256)
pip install requests cryptography --break-system-packages
python3 sync_jwks_to_apisix.py \
  --jwks-url http://localhost:8080/.well-known/jwks.json \
  --apisix-admin-url http://localhost:9180 \
  --apisix-admin-key "$APISIX_ADMIN_KEY" \
  --consumer-key simpax-backend
```

> Panduan pengujian lengkap (test case untuk APISIX, OpenLDAP/RBAC, dan integrasi
> end-to-end) ada di [`TEST_GUIDE_APACHE_STACK.md`](TEST_GUIDE_APACHE_STACK.md) (root repo) — dipersiapkan untuk Sekar (Security Tester).

---

_Repository ini dikelola sebagai bagian dari simulasi akademik untuk Mata Kuliah Keamanan Jaringan._
