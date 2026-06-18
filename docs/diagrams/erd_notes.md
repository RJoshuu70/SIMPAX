# Catatan Skema ERD - Modul Backend (Joshua)

## Entity dari PRD (tidak diubah)
- ROLES
- USERS
- BUDGETS_TRANSACTIONS

## Entity baru - Modul Saldo & Saham

### WALLET_BALANCES
| Kolom | Tipe | Constraint | Keterangan |
|---|---|---|---|
| wallet_id | UUID | PK | |
| user_id | UUID | FK -> USERS.user_id | |
| cash_balance | DECIMAL(18,2) | NOT NULL, DEFAULT 0 | |
| currency | VARCHAR(10) | NOT NULL, DEFAULT 'IDR' | |
| updated_at | TIMESTAMP | NOT NULL | |

### STOCK_HOLDINGS
| Kolom | Tipe | Constraint | Keterangan |
|---|---|---|---|
| holding_id | UUID | PK | |
| user_id | UUID | FK -> USERS.user_id | |
| ticker_symbol | VARCHAR(10) | NOT NULL | format IDX: BBCA.JK, TLKM.JK |
| exchange | VARCHAR(20) | NOT NULL, DEFAULT 'IDX' | extensible utk exchange lain |
| quantity | DECIMAL(18,4) | NOT NULL | presisi lebih utk lot/fraksional |
| avg_buy_price | DECIMAL(18,2) | NOT NULL | |
| created_at | TIMESTAMP | NOT NULL | |

### STOCK_PRICE_CACHES
| Kolom | Tipe | Constraint | Keterangan |
|---|---|---|---|
| ticker_symbol | VARCHAR(10) | PK | |
| exchange | VARCHAR(20) | NOT NULL | |
| last_price | DECIMAL(18,2) | NOT NULL | |
| fetched_at | TIMESTAMP | NOT NULL | dipakai utk cache TTL/resilience |

## Keputusan Desain
- ticker_symbol VARCHAR(10): cukup utk format "XXXX.JK" (7 char) + buffer
- Dipisah dari BUDGETS_TRANSACTIONS: Single Responsibility per modul
- exchange ditambahkan agar tidak hardcode IDX-only
