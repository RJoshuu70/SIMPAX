package id.ac.upnvj.simpax.service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class WalletBalanceDto {

    /**
     * Request pembuatan wallet baru. Hanya currency yang diinput user -
     * cashBalance SELALU dimulai dari 0 di service (tidak boleh diinput
     * langsung), supaya tidak ada jalan bagi client untuk "menyuntik" saldo
     * awal tanpa melalui mekanisme deposit yang tercatat.
     */
    public record CreateWalletRequest(
        @NotBlank(message = "Currency tidak boleh kosong")
        @Size(max = 10, message = "Currency maksimal 10 karakter")
        String currency
    ) {}

    /**
     * Request generik untuk operasi deposit/withdraw - sengaja dibuat satu
     * record yang dipakai ulang (bukan DepositRequest dan WithdrawRequest
     * terpisah) karena bentuk datanya identik (cuma satu field: amount).
     * Perbedaan semantik "tambah" vs "kurang" ditentukan oleh endpoint/method
     * yang dipanggil, bukan oleh shape data itu sendiri - prinsip DRY tanpa
     * mengorbankan kejelasan nama endpoint di Controller.
     */
    public record AmountRequest(
        @NotNull(message = "Amount tidak boleh kosong")
        @DecimalMin(value = "0.01", message = "Amount harus lebih dari 0")
        BigDecimal amount
    ) {}

    public record WalletResponse(
        UUID walletId,
        BigDecimal cashBalance,
        String currency,
        Instant updatedAt
    ) {}
}
