package id.ac.upnvj.simpax.service.dto;

import id.ac.upnvj.simpax.domain.TransactionStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class BudgetsTransactionDto {

    /**
     * Request CREATE transaksi (hanya field yang boleh diinput Staf).
     * amount saja yang diinput - tax_amount dihitung otomatis oleh service
     * berdasarkan rate PPN yang berlaku (11% per PMK 2022).
     */
    public record CreateRequest(
        @NotNull(message = "Amount tidak boleh kosong")
        @DecimalMin(value = "0.01", message = "Amount harus lebih dari 0")
        BigDecimal amount
    ) {}

    /** Response transaksi - mengembalikan data lengkap untuk ditampilkan di UI */
    public record TransactionResponse(
        UUID transactionId,
        BigDecimal amount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,  // amount + taxAmount, dihitung di mapper
        TransactionStatus status,
        String createdByUsername,
        String approvedByUsername,
        Instant createdAt,
        Instant approvedAt
    ) {}
}
