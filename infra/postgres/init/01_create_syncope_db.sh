#!/bin/bash
# =============================================================
# Script ini dijalankan otomatis oleh PostgreSQL saat container
# pertama kali dibuat (volume belum ada). Script ini membuat
# database terpisah untuk Apache Syncope agar tidak berbagi
# skema dengan database utama SIMPAX backend.
#
# Referensi: https://hub.docker.com/_/postgres
# (bagian "Initialization scripts")
# =============================================================
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE syncope
        WITH OWNER = $POSTGRES_USER
        ENCODING = 'UTF8'
        LC_COLLATE = 'en_US.utf8'
        LC_CTYPE = 'en_US.utf8'
        TEMPLATE = template0;
    GRANT ALL PRIVILEGES ON DATABASE syncope TO $POSTGRES_USER;
EOSQL
echo "Database 'syncope' berhasil dibuat untuk Apache Syncope."
