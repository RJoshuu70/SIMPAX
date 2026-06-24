#!/usr/bin/env python3
"""
sync_jwks_to_apisix.py

Mengambil public key JWT (format JWKS) dari backend SIMPAX, mengonversinya
menjadi PEM, lalu mendaftarkan/memperbarui Consumer APISIX dengan plugin
jwt-auth (algoritma RS256).

STRATEGI PENCOCOKAN TOKEN -> CONSUMER (Opsi B, disepakati di sesi sebelumnya):
  Plugin jwt-auth APISIX butuh satu klaim di payload JWT untuk mencocokkan
  token masuk ke Consumer yang tepat. Defaultnya nama klaim itu "key", tapi
  bisa diarahkan ke klaim apa saja lewat parameter route-level "key_claim_name".

  TokenProvider.java milik Joshua TIDAK menyisipkan klaim "key", tapi SUDAH
  menyisipkan klaim "iss" dengan nilai konstan "simpax-backend" untuk semua
  token yang diterbitkan. Maka:
    - Consumer didaftarkan dengan field "key" (identifier internal APISIX,
      BUKAN nama klaim) = nilai dari --consumer-key (default: "simpax-backend")
    - Saat membuat Route nanti, plugin jwt-auth pada route tersebut WAJIB
      diset dengan key_claim_name: "iss" -- ini TIDAK dilakukan oleh script
      ini (scope script ini cuma level Consumer), tapi WAJIB diingat saat
      menulis konfigurasi Route.

  Konsekuensinya: tidak perlu mengubah satu baris pun kode TokenProvider.java.

Requirements:
    pip install requests cryptography --break-system-packages

Contoh pemakaian:
    python3 sync_jwks_to_apisix.py \
      --jwks-url http://localhost:8080/.well-known/jwks.json \
      --apisix-admin-url http://localhost:9180 \
      --apisix-admin-key <isi_APISIX_ADMIN_KEY_dari_.env> \
      --consumer-key simpax-backend

    (Lihat juga: --dry-run untuk melihat payload tanpa benar-benar mengirim
    request ke Admin API APISIX.)
"""

import argparse
import base64
import sys
import json

import requests
from cryptography.hazmat.primitives.asymmetric.rsa import RSAPublicNumbers
from cryptography.hazmat.primitives import serialization


def b64url_to_int(value: str) -> int:
    """Decode Base64URL (tanpa padding, sesuai field JWKS 'n' dan 'e') jadi integer."""
    padding = "=" * (-len(value) % 4)
    raw_bytes = base64.urlsafe_b64decode(value + padding)
    return int.from_bytes(raw_bytes, byteorder="big")


def fetch_jwks(jwks_url: str) -> dict:
    try:
        resp = requests.get(jwks_url, timeout=5)
        resp.raise_for_status()
    except requests.exceptions.RequestException as exc:
        print(f"[ERROR] Gagal mengambil JWKS dari {jwks_url}: {exc}", file=sys.stderr)
        sys.exit(1)

    try:
        data = resp.json()
    except ValueError:
        print(f"[ERROR] Response dari {jwks_url} bukan JSON valid.", file=sys.stderr)
        sys.exit(1)

    keys = data.get("keys", [])
    if not keys:
        print(f"[ERROR] Field 'keys' kosong atau tidak ada di response JWKS.", file=sys.stderr)
        sys.exit(1)

    rsa_key = next((k for k in keys if k.get("kty") == "RSA"), None)
    if rsa_key is None:
        print("[ERROR] Tidak ditemukan key bertipe RSA pada JWKS.", file=sys.stderr)
        sys.exit(1)

    if "n" not in rsa_key or "e" not in rsa_key:
        print("[ERROR] Key RSA pada JWKS tidak punya field 'n' dan/atau 'e'.", file=sys.stderr)
        sys.exit(1)

    return rsa_key


def jwk_to_pem(rsa_key: dict) -> str:
    n = b64url_to_int(rsa_key["n"])
    e = b64url_to_int(rsa_key["e"])
    public_numbers = RSAPublicNumbers(e=e, n=n)
    public_key = public_numbers.public_key()
    pem_bytes = public_key.public_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    return pem_bytes.decode("utf-8")


def to_safe_username(consumer_key: str) -> str:
    """
    Field 'username' pada Consumer APISIX wajib match regex ^[a-zA-Z0-9_]+$
    (tidak boleh ada tanda hubung). Field 'key' pada plugin jwt-auth TIDAK
    tunduk pada aturan ini -- itu cuma string konfigurasi plugin, dan justru
    HARUS sama persis dengan nilai klaim 'iss' yang diterbitkan TokenProvider.java
    (yang memang memakai tanda hubung, "simpax-backend").
    Jadi: username Consumer cuma identifier internal APISIX, dibuat aman dengan
    mengganti '-' jadi '_'; field 'key' tetap memakai nilai asli consumer_key.
    """
    return consumer_key.replace("-", "_")


def generate_placeholder_private_key() -> str:
    """
    Skema Consumer plugin jwt-auth di APISIX 3.10.x mewajibkan field
    'private_key' hadir bersamaan dengan 'public_key' saat algoritma RS256
    dipilih (lihat dependencies.algorithm.oneOf pada jwt-auth.lua versi ini),
    meskipun secara fungsional private_key HANYA dipakai APISIX untuk
    endpoint /apisix/plugin/jwt/sign (penerbitan token oleh APISIX sendiri).

    Kita TIDAK memakai endpoint itu -- TokenProvider.java Joshua tetap satu-
    satunya pihak yang menerbitkan & menandatangani token, dengan private key
    yang tidak pernah keluar dari backend. Jadi private key di sini hanya
    placeholder sintaksis untuk memenuhi validasi skema, BUKAN kunci privat
    yang sungguhan dipakai untuk apa pun -- jangan dianggap sebagai secret asli.
    """
    from cryptography.hazmat.primitives.asymmetric import rsa

    dummy_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    pem_bytes = dummy_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )
    return pem_bytes.decode("utf-8")


def build_consumer_payload(consumer_key: str, public_key_pem: str) -> dict:
    return {
        "username": to_safe_username(consumer_key),
        "plugins": {
            "jwt-auth": {
                "key": consumer_key,
                "algorithm": "RS256",
                "public_key": public_key_pem,
                # Placeholder wajib karena skema APISIX 3.10.x -- lihat docstring
                # generate_placeholder_private_key(). Tidak dipakai untuk verifikasi
                # token nyata; verifikasi murni memakai public_key di atas.
                "private_key": generate_placeholder_private_key(),
            }
        },
    }


def register_consumer(apisix_admin_url: str, apisix_admin_key: str, safe_username: str, payload: dict) -> None:
    url = f"{apisix_admin_url.rstrip('/')}/apisix/admin/consumers/{safe_username}"
    try:
        resp = requests.put(
            url,
            headers={"X-API-KEY": apisix_admin_key, "Content-Type": "application/json"},
            json=payload,
            timeout=5,
        )
    except requests.exceptions.RequestException as exc:
        print(f"[ERROR] Gagal menghubungi Admin API APISIX di {url}: {exc}", file=sys.stderr)
        sys.exit(1)

    if resp.status_code not in (200, 201):
        print(f"[ERROR] Admin API mengembalikan status {resp.status_code}: {resp.text}", file=sys.stderr)
        sys.exit(1)

    print(f"[OK] Consumer '{safe_username}' (key plugin jwt-auth: '{payload['plugins']['jwt-auth']['key']}') berhasil didaftarkan/diperbarui di APISIX.")
    print(f"[OK] Response: {resp.status_code} {resp.text}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--jwks-url", required=True, help="URL endpoint JWKS backend, contoh: http://localhost:8080/.well-known/jwks.json")
    parser.add_argument("--apisix-admin-url", required=True, help="Base URL Admin API APISIX, contoh: http://localhost:9180")
    parser.add_argument("--apisix-admin-key", required=False, help="Nilai header X-API-KEY (isi dari APISIX_ADMIN_KEY di .env). Wajib kecuali --dry-run.")
    parser.add_argument(
        "--consumer-key",
        default="simpax-backend",
        help=(
            "Identifier Consumer di APISIX (field 'key' pada plugin jwt-auth). "
            "Default 'simpax-backend' -- HARUS SAMA dengan nilai klaim 'iss' yang "
            "diterbitkan TokenProvider.java, karena Route nantinya akan dikonfigurasi "
            "dengan key_claim_name: 'iss' untuk mencocokkan token tanpa perlu klaim 'key'."
        ),
    )
    parser.add_argument("--dry-run", action="store_true", help="Tampilkan payload yang AKAN dikirim, tanpa benar-benar memanggil Admin API APISIX.")
    args = parser.parse_args()

    if not args.dry_run and not args.apisix_admin_key:
        print("[ERROR] --apisix-admin-key wajib diisi kecuali memakai --dry-run.", file=sys.stderr)
        sys.exit(1)

    print(f"[INFO] Mengambil JWKS dari {args.jwks_url} ...")
    rsa_key = fetch_jwks(args.jwks_url)

    print("[INFO] Mengonversi JWK (n, e) menjadi PEM ...")
    public_key_pem = jwk_to_pem(rsa_key)

    payload = build_consumer_payload(args.consumer_key, public_key_pem)

    if args.dry_run:
        print("[DRY-RUN] Payload yang akan dikirim ke Admin API APISIX:")
        print(json.dumps(payload, indent=2))
        print(
            "\n[DRY-RUN] Pengingat: saat membuat Route, set plugin jwt-auth pada "
            "route tersebut dengan key_claim_name: \"iss\" -- bukan default \"key\" --"
            " agar token Joshua (yang tidak punya klaim 'key') tetap bisa dicocokkan."
        )
        return

    print(f"[INFO] Mendaftarkan Consumer '{to_safe_username(args.consumer_key)}' (key plugin jwt-auth: '{args.consumer_key}') ke {args.apisix_admin_url} ...")
    register_consumer(args.apisix_admin_url, args.apisix_admin_key, to_safe_username(args.consumer_key), payload)


if __name__ == "__main__":
    main()