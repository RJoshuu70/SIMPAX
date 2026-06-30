#!/bin/bash
# =============================================================
# Manual Test Script - Modul WalletBalance & StockHolding
#
# Status: pengganti automated test (JUnit) untuk sementara, karena
# keputusan tim menunda automated testing demi mengejar deadline UAS
# (didokumentasikan sebagai keputusan sadar, bukan gap diam-diam -
# lihat README §6).
#
# Cara pakai (Git Bash / MINGW64):
#   chmod +x test_wallet_stock_module.sh
#   ./test_wallet_stock_module.sh
#
# Prasyarat: stack docker compose sudah running (backend di port 8080),
# dan user "testuser01" (role STAFF) sudah terdaftar - kalau belum,
# jalankan register dulu (lihat KOMENTAR_REGISTER di bawah).
# =============================================================

BASE_URL="http://localhost:8080"
PASS=0
FAIL=0

# --- util: print hasil step dengan label PASS/FAIL ---
check_status() {
  local label="$1"
  local expected="$2"
  local actual="$3"
  if [ "$actual" == "$expected" ]; then
    echo "✅ PASS - $label (HTTP $actual, expected $expected)"
    PASS=$((PASS+1))
  else
    echo "❌ FAIL - $label (HTTP $actual, expected $expected)"
    FAIL=$((FAIL+1))
  fi
}

# --- util: ekstrak nilai string dari JSON flat tanpa dependency jq ---
extract_str() {
  echo "$1" | sed -n "s/.*\"$2\":\"\([^\"]*\)\".*/\1/p"
}
# --- util: ekstrak nilai numerik dari JSON flat ---
extract_num() {
  echo "$1" | sed -n "s/.*\"$2\":\([0-9.\-]*\).*/\1/p"
}

echo "============================================================"
echo " STEP 0 - Register & Login User Utama (testuser01)"
echo "============================================================"

# KOMENTAR_REGISTER: skip langkah ini kalau testuser01 sudah pernah
# didaftarkan sebelumnya (akan dapat 400 "username sudah ada" - itu OK,
# tidak menggagalkan skenario login di bawahnya).
curl -s -o /dev/null -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser01","email":"test@example.com","password":"TestPass123!","roleName":"STAFF"}'

LOGIN_RESP=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser01","password":"TestPass123!"}')
TOKEN=$(extract_str "$LOGIN_RESP" "token")

if [ -z "$TOKEN" ]; then
  echo "❌ FATAL - Tidak bisa login testuser01. Cek apakah backend & Syncope sehat. Script dihentikan."
  exit 1
fi
echo "✅ Login berhasil, token didapat (panjang: ${#TOKEN} karakter)"
AUTH="Authorization: Bearer $TOKEN"
echo ""

echo "============================================================"
echo " STEP 1 - Create Wallet (currency IDR)"
echo "============================================================"
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/wallets" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"currency":"IDR"}')
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -n1)
echo "Response: $BODY"
if [ "$CODE" == "201" ] || { [ "$CODE" == "400" ] && echo "$BODY" | grep -q "sudah ada"; }; then
  echo "✅ PASS - Create wallet IDR (HTTP $CODE - 201 baru dibuat ATAU 400 karena sudah ada dari run sebelumnya, keduanya valid)"
  PASS=$((PASS+1))
else
  echo "❌ FAIL - Create wallet IDR (HTTP $CODE, expected 201 atau 400 'sudah ada')"
  FAIL=$((FAIL+1))
fi
WALLET_ID=$(extract_str "$BODY" "walletId")
echo "WALLET_ID = $WALLET_ID"
echo ""
echo "EXPEKTASI: HTTP 201, body berisi walletId (UUID), cashBalance 0.00 (atau 0.0/0), currency IDR."
echo "Kalau kamu jalankan ulang script ini (wallet IDR sudah ada dari run sebelumnya),"
echo "step ini WAJAR mengembalikan 400 'Wallet dengan currency IDR sudah ada' - lanjutkan saja ke Step 2."
echo ""

# Jika create gagal karena wallet sudah ada, ambil walletId yang sudah ada
if [ -z "$WALLET_ID" ]; then
  MINE_RESP=$(curl -s -H "$AUTH" "$BASE_URL/api/wallets/mine")
  WALLET_ID=$(extract_str "$MINE_RESP" "walletId")
  echo "Mengambil WALLET_ID dari wallet yang sudah ada: $WALLET_ID"
fi
echo ""

echo "============================================================"
echo " STEP 2 - Deposit 1.000.000 ke wallet"
echo "============================================================"
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/wallets/$WALLET_ID/deposit" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"amount":1000000}')
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -n1)
echo "Response: $BODY"
check_status "Deposit 1.000.000" "200" "$CODE"
echo "EXPEKTASI: HTTP 200, cashBalance bertambah 1.000.000 dari nilai sebelumnya."
echo ""

echo "============================================================"
echo " STEP 3 - Withdraw 200.000 (harus berhasil)"
echo "============================================================"
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/wallets/$WALLET_ID/withdraw" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"amount":200000}')
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -n1)
echo "Response: $BODY"
check_status "Withdraw 200.000 (cukup saldo)" "200" "$CODE"
CASH_AFTER_WITHDRAW=$(extract_num "$BODY" "cashBalance")
echo "Saldo setelah withdraw: $CASH_AFTER_WITHDRAW"
echo "EXPEKTASI: HTTP 200, cashBalance berkurang 200.000."
echo ""

echo "============================================================"
echo " STEP 4 - Withdraw 999.999.999 (HARUS GAGAL - saldo tidak cukup)"
echo "============================================================"
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/wallets/$WALLET_ID/withdraw" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"amount":999999999}')
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -n1)
echo "Response: $BODY"
check_status "Withdraw melebihi saldo (negative test)" "400" "$CODE"
echo "EXPEKTASI: HTTP 400 dengan pesan error 'Saldo tidak cukup'. Ini SKENARIO NEGATIF -"
echo "kalau hasilnya 200, justru itu BUG (artinya validasi saldo tidak jalan)."
echo ""

echo "============================================================"
echo " STEP 5 - Get My Wallets"
echo "============================================================"
RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" "$BASE_URL/api/wallets/mine")
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -n1)
echo "Response: $BODY"
check_status "Get my wallets" "200" "$CODE"
echo ""

echo "============================================================"
echo " STEP 6 - Buy Saham BBCA.JK qty 10"
echo "============================================================"
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/stocks/buy" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"tickerSymbol":"BBCA.JK","exchange":"IDX","quantity":10}')
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -n1)
echo "Response: $BODY"
check_status "Buy BBCA.JK qty 10" "200" "$CODE"
echo "EXPEKTASI: HTTP 200, body berisi holding (quantity 10, avgBuyPrice = harga mock saat ini),"
echo "walletCashBalanceAfter LEBIH KECIL dari saldo sebelum buy, realizedPnl = null."
echo ""

echo "============================================================"
echo " STEP 7 - Get Portfolio"
echo "============================================================"
RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" "$BASE_URL/api/stocks/portfolio")
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -n1)
echo "Response: $BODY"
check_status "Get portfolio" "200" "$CODE"
echo "EXPEKTASI: HTTP 200, array berisi minimal 1 holding BBCA.JK dengan currentPrice,"
echo "marketValue, dan unrealizedPnl terisi (unrealizedPnl = 0 jika currentPrice == avgBuyPrice,"
echo "karena harga mock deterministik tidak berubah antar request untuk ticker yang sama)."
echo ""

echo "============================================================"
echo " STEP 8 - Sell Saham BBCA.JK qty 5 (sebagian)"
echo "============================================================"
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/stocks/sell" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"tickerSymbol":"BBCA.JK","quantity":5}')
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -n1)
echo "Response: $BODY"
check_status "Sell BBCA.JK qty 5" "200" "$CODE"
echo "EXPEKTASI: HTTP 200, holding.quantity menjadi 5 (10-5), realizedPnl TERISI (bukan null),"
echo "walletCashBalanceAfter bertambah dari hasil penjualan."
echo ""

echo "============================================================"
echo " STEP 9 - Sell Saham BBCA.JK qty 999 (HARUS GAGAL - melebihi kepemilikan)"
echo "============================================================"
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/stocks/sell" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"tickerSymbol":"BBCA.JK","quantity":999}')
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -n1)
echo "Response: $BODY"
check_status "Sell melebihi kepemilikan (negative test)" "400" "$CODE"
echo ""

echo "============================================================"
echo " STEP 10 - Ownership Violation Test (HARUS 403)"
echo " User kedua (testuser02) mencoba akses wallet milik testuser01"
echo "============================================================"

curl -s -o /dev/null -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser02","email":"test2@example.com","password":"TestPass123!","roleName":"STAFF"}'

LOGIN2_RESP=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser02","password":"TestPass123!"}')
TOKEN2=$(extract_str "$LOGIN2_RESP" "token")
AUTH2="Authorization: Bearer $TOKEN2"

RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/wallets/$WALLET_ID/deposit" \
  -H "$AUTH2" -H "Content-Type: application/json" \
  -d '{"amount":1}')
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -n1)
echo "Response: $BODY"
check_status "testuser02 akses wallet milik testuser01 (HARUS DITOLAK)" "403" "$CODE"
echo "EXPEKTASI: HTTP 403 Forbidden. Ini adalah pembuktian LANGSUNG bahwa ownership-based"
echo "authorization di WalletBalanceService benar-benar berfungsi - bukan cuma 'sepertinya benar'"
echo "secara teori dari membaca kode, tapi terbukti menolak user lain secara nyata."
echo ""

echo "============================================================"
echo " RINGKASAN"
echo "============================================================"
echo "PASS : $PASS"
echo "FAIL : $FAIL"
if [ "$FAIL" -eq 0 ]; then
  echo "✅ SEMUA SKENARIO SESUAI EKSPEKTASI."
else
  echo "❌ ADA $FAIL SKENARIO YANG TIDAK SESUAI EKSPEKTASI - cek detail di atas."
fi
