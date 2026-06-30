# simpax-web

Frontend Standalone React + Vite SPA untuk SIMPAX — sesuai keputusan
arsitektur yang didokumentasikan di README repository utama §5.1.

## Cara menjalankan

Prasyarat: backend + APISIX + etcd + Syncope sudah running via
`docker compose up -d` di root project (lihat README utama §8-9).

```bash
npm install
npm run dev
```

Buka `http://localhost:3000`. Vite dev server mem-proxy semua request
`/api/*` ke APISIX (`http://localhost:9080`), **bukan** langsung ke
backend (`8080`) — konfigurasi ada di `vite.config.js`, sesuai prinsip
Zero Trust yang sudah disepakati: frontend tidak pernah memanggil
backend secara langsung.

**Prasyarat tambahan, WAJIB sebelum testing login/register dari UI ini:**
jalankan `scripts/setup_apisix_routes.sh` (lihat README utama §9) supaya
route `/api/*` di APISIX sudah terdaftar. Tanpa ini, semua request dari
frontend akan ditolak APISIX (404), meski backend-nya sendiri sehat.

## Struktur

```
src/
├── components/    # Layout, ProtectedRoute (reusable, tidak terikat 1 halaman)
├── contexts/       # AuthContext (state JWT + user info)
├── hooks/          # useCountUp (animasi signature dashboard)
├── pages/          # Satu file per route
└── services/       # api.js (SATU pintu untuk semua request HTTP) + format.js
```

## Keputusan desain (ringkas — detail di Javadoc/komentar tiap file)

- **Token disimpan di `sessionStorage`**, bukan `localStorage` — trade-off
  sadar demi keamanan (token hilang saat tab ditutup), lihat
  `AuthContext.jsx`.
- **Semua request HTTP wajib lewat `services/api.js`** — tidak ada
  komponen yang memanggil `fetch()` langsung, supaya penanganan 401 dan
  base URL terpusat di satu tempat.
- **Role-aware UI** di halaman Transaksi (tombol Setuju/Tolak hanya
  muncul untuk MANAGER/DIREKSI) — ini HANYA kosmetik, otorisasi
  sesungguhnya tetap di backend (`SecurityConfig.java` + APISIX
  `jwt-auth`).

## Status & batasan saat ini

- Modul yang sudah terhubung penuh ke backend: Auth (login/register),
  Wallet & Stock (Saldo & Saham), Budget Transactions.
- Modul yang BELUM ada UI-nya karena backend-nya sendiri belum dibangun
  (lihat README utama §Tahap 6): Kalkulator Pajak, Kwitansi & Faktur.
- Harga saham di halaman Dompet & Saham memakai data simulasi
  (`StockPriceCacheService` mock) — bukan harga pasar riil.
