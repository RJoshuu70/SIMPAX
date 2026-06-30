#!/usr/bin/env bash
# =====================================================================
# setup_apisix_routes.sh
#
# Mendaftarkan Route APISIX untuk backend SIMPAX via Admin API.
# Dijalankan SEKALI setelah APISIX + etcd + backend sudah Up, dan
# SETELAH sync_jwks_to_apisix.py sudah dijalankan (Consumer harus ada
# dulu sebelum Route yang mereferensikan jwt-auth dipakai).
#
# Dua Route yang dibuat:
#   1. simpax-auth-public   -> /api/auth/*  dan /.well-known/jwks.json
#      Tanpa plugin jwt-auth (memang belum punya token saat login/register).
#      Plugin limit-count dipasang untuk mitigasi brute-force.
#   2. simpax-api-protected -> /api/* (CATCH-ALL, direvisi dari semula
#      hanya /api/transactions/*)
#      Plugin jwt-auth AKTIF, key_claim_name diset "iss" (BUKAN default "key")
#      karena TokenProvider.java menyisipkan klaim "iss", bukan "key" -
#      lihat catatan di sync_jwks_to_apisix.py.
#
#      KEPUTUSAN ARSITEKTUR (revisi): semula route #2 didaftarkan SPESIFIK
#      per modul ("/api/transactions/*" saja). Begitu modul Saldo & Saham
#      (/api/wallets, /api/stocks) ditambahkan, pendekatan itu jadi rapuh -
#      setiap modul baru (nanti: /api/tax, /api/receipts) butuh edit script
#      ini lagi dan re-run setup_apisix_routes.sh, padahal mudah lupa. Maka
#      route #2 digeneralisasi jadi "/api/*" sebagai catch-all untuk semua
#      endpoint terlindungi. APISIX memilih route paling SPESIFIK dulu saat
#      ada beberapa route yang match (longest prefix match), jadi
#      "/api/auth/*" tetap menang dibanding "/api/*" untuk request ke
#      /api/auth/login - tidak ada konflik. Trade-off: rate-limit jadi
#      seragam 100 req/menit untuk SEMUA endpoint terlindungi, tidak lagi
#      bisa diatur berbeda per modul lewat route APISIX (kalau suatu saat
#      perlu rate-limit lebih ketat khusus modul tertentu, itu perlu route
#      terpisah lagi dengan uri lebih spesifik didaftarkan SEBELUM catch-all
#      ini).
#
# Otorisasi by-role (STAFF/MANAGER/AUDITOR/DIREKSI) TETAP di backend
# (SecurityConfig.java) - APISIX di sini hanya validasi signature JWT,
# bukan mendobel logika RBAC.
#
# Requirements: APISIX_ADMIN_KEY harus sudah diisi nilai asli di .env
# (bukan placeholder "ganti_dengan_admin_key_apisix").
#
# Pemakaian:
#   ./scripts/setup_apisix_routes.sh
#   (baca .env di root repo secara otomatis; atau export manual sebelum run)
# =====================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

# Baca .env kalau ada, supaya APISIX_ADMIN_KEY & port tidak perlu diketik manual
if [[ -f "$ROOT_DIR/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env"
  set +a
fi

APISIX_ADMIN_URL="${APISIX_ADMIN_URL:-http://localhost:${APISIX_ADMIN_PORT:-9180}}"
ADMIN_KEY="${APISIX_ADMIN_KEY:-}"
BACKEND_UPSTREAM="${BACKEND_UPSTREAM:-simpax-backend:8080}"

if [[ -z "$ADMIN_KEY" || "$ADMIN_KEY" == "ganti_dengan_admin_key_apisix" ]]; then
  echo "[ERROR] APISIX_ADMIN_KEY belum diisi nilai asli di .env. Berhenti." >&2
  exit 1
fi

echo "[INFO] APISIX Admin API: $APISIX_ADMIN_URL"
echo "[INFO] Upstream backend: $BACKEND_UPSTREAM"
echo ""

# ---------------------------------------------------------------------
# Route 1: Endpoint publik (login, register, JWKS) - TANPA jwt-auth,
# TAPI dengan rate-limit untuk mitigasi brute-force credential.
# ---------------------------------------------------------------------
echo "[INFO] Mendaftarkan route 'simpax-auth-public' ..."
curl -sS -X PUT "$APISIX_ADMIN_URL/apisix/admin/routes/simpax-auth-public" \
  -H "X-API-KEY: $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "uri": "/api/auth/*",
    "methods": ["POST", "OPTIONS"],
    "priority": 10,
    "plugins": {
      "limit-count": {
        "count": 10,
        "time_window": 60,
        "key_type": "var",
        "key": "remote_addr",
        "rejected_code": 429,
        "policy": "local"
      }
    },
    "upstream": {
      "type": "roundrobin",
      "nodes": {
        "'"$BACKEND_UPSTREAM"'": 1
      }
    }
  }'
echo ""

echo "[INFO] Mendaftarkan route 'simpax-jwks-public' ..."
curl -sS -X PUT "$APISIX_ADMIN_URL/apisix/admin/routes/simpax-jwks-public" \
  -H "X-API-KEY: $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "uri": "/.well-known/jwks.json",
    "methods": ["GET", "OPTIONS"],
    "priority": 10,
    "upstream": {
      "type": "roundrobin",
      "nodes": {
        "'"$BACKEND_UPSTREAM"'": 1
      }
    }
  }'
echo ""

# ---------------------------------------------------------------------
# Route 2: Endpoint terlindungi (CATCH-ALL /api/*) - WAJIB jwt-auth valid.
# key_claim_name "iss" karena TokenProvider.java pakai klaim "iss",
# bukan "key" (lihat sync_jwks_to_apisix.py untuk penjelasan lengkap).
# Mencakup /api/transactions/*, /api/wallets/*, /api/stocks/*, dan modul
# Tahap 6 berikutnya (/api/tax, /api/receipts) - TIDAK perlu didaftarkan
# satu-satu lagi (lihat catatan keputusan arsitektur di atas).
# Otorisasi per-role (MANAGER/DIREKSI utk approve/reject, dst) sudah
# ditangani backend sendiri lewat SecurityConfig.java - TIDAK didobel
# di sini.
# ---------------------------------------------------------------------
echo "[INFO] Mendaftarkan route 'simpax-api-protected' (catch-all /api/*) ..."
curl -sS -X PUT "$APISIX_ADMIN_URL/apisix/admin/routes/simpax-api-protected" \
  -H "X-API-KEY: $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "uri": "/api/*",
    "plugins": {
      "jwt-auth": {
        "key_claim_name": "iss"
      },
      "limit-count": {
        "count": 100,
        "time_window": 60,
        "key_type": "var",
        "key": "remote_addr",
        "rejected_code": 429,
        "policy": "local"
      }
    },
    "upstream": {
      "type": "roundrobin",
      "nodes": {
        "'"$BACKEND_UPSTREAM"'": 1
      }
    }
  }'
echo ""

echo "[OK] Semua route terdaftar. Verifikasi dengan:"
echo "  curl -s -H \"X-API-KEY: \$APISIX_ADMIN_KEY\" $APISIX_ADMIN_URL/apisix/admin/routes | python3 -m json.tool"