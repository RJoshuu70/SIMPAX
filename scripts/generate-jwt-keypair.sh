#!/bin/bash
# ============================================================
# Generate RSA keypair untuk JWT signing (RS256) - SIMPAX Backend
#
# Jalankan SEKALI di laptop Anda sebelum pertama kali menjalankan
# aplikasi. Output: dua file .pem di simpax-app/config/keys/
#
# PENTING:
# - simpax-private.pem JANGAN PERNAH dikirim ke siapapun atau
#   di-commit ke Git (sudah tercakup pola di .gitignore root).
# - simpax-public.pem BOLEH dan PERLU diserahkan ke Zaskia untuk
#   dikonfigurasikan pada plugin jwt-auth Apache APISIX.
#
# Cara pakai:
#   chmod +x scripts/generate-jwt-keypair.sh
#   ./scripts/generate-jwt-keypair.sh
# ============================================================

set -e  # Hentikan script jika ada command yang gagal (fail-fast)

KEY_DIR="simpax-app/config/keys"
PRIVATE_KEY="$KEY_DIR/simpax-private.pem"
PUBLIC_KEY="$KEY_DIR/simpax-public.pem"

mkdir -p "$KEY_DIR"

if [ -f "$PRIVATE_KEY" ]; then
  echo "⚠️  Private key sudah ada di $PRIVATE_KEY"
  read -p "Timpa dengan key baru? (y/N): " confirm
  if [ "$confirm" != "y" ]; then
    echo "Dibatalkan. Key lama tetap dipakai."
    exit 0
  fi
fi

echo "🔑 Generating RSA 2048-bit keypair untuk JWT RS256..."

# Generate private key dalam format PKCS#8 (format yang dibaca RsaKeyLoader.java)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$PRIVATE_KEY"

# Derive public key dari private key
openssl rsa -pubout -in "$PRIVATE_KEY" -out "$PUBLIC_KEY"

chmod 600 "$PRIVATE_KEY"  # Hanya owner yang bisa baca/tulis private key

echo "✅ Selesai."
echo "   Private key : $PRIVATE_KEY (JANGAN dibagikan/commit)"
echo "   Public key  : $PUBLIC_KEY (serahkan ke Zaskia untuk konfigurasi APISIX)"
