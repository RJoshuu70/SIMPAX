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
#   2. simpax-api-protected -> /api/transactions/*
#      Plugin jwt-auth AKTIF, key_claim_name diset "iss" (BUKAN default "key")
#      karena TokenProvider.java menyisipkan klaim "iss", bukan "key" -
#      lihat catatan di sync_jwks_to_apisix.py.
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
    "upstream": {
      "type": "roundrobin",
      "nodes": {
        "'"$BACKEND_UPSTREAM"'": 1
      }
    }
  }'
echo ""

# ---------------------------------------------------------------------
# Route 2: Endpoint terlindungi (transaksi) - WAJIB jwt-auth valid.
# key_claim_name "iss" karena TokenProvider.java pakai klaim "iss",
# bukan "key" (lihat sync_jwks_to_apisix.py untuk penjelasan lengkap).
# Otorisasi per-role (MANAGER/DIREKSI utk approve/reject, dst) sudah
# ditangani backend sendiri lewat SecurityConfig.java - TIDAK didobel
# di sini.
# ---------------------------------------------------------------------
echo "[INFO] Mendaftarkan route 'simpax-api-protected' ..."
curl -sS -X PUT "$APISIX_ADMIN_URL/apisix/admin/routes/simpax-api-protected" \
  -H "X-API-KEY: $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "uris": ["/api/transactions", "/api/transactions/*"],
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