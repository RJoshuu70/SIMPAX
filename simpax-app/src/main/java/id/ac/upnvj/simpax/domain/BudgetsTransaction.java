package id.ac.upnvj.simpax.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity BudgetsTransaction sesuai PRD Bab 10.4.
 *
 * <p>Entity ini adalah inti dari prinsip Separation of Duties (SoD) pada
 * SIMPAX: satu transaksi memiliki DUA relasi terpisah ke User - createdBy
 * (Staf yang menginput) dan approvedBy (Manajer yang menyetujui). Keduanya
 * TIDAK BOLEH menjadi user yang sama pada satu transaksi yang sama; validasi
 * ini akan diterapkan pada layer Service (lihat BudgetsTransactionService),
 * bukan pada layer Entity, karena aturan bisnis semacam ini bukan tanggung
 * jawab struktural Entity.
 *
 * <p>Status transaksi menggunakan String (bukan enum Java murni) yang
 * dibatasi nilainya melalui TransactionStatus - lihat penjelasan di bawah.
 */
@Entity
@Table(name = "budgets_transactions")
@Getter
@Setter
@NoArgsConstructor
public class BudgetsTransaction implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "transaction_id")
    private UUID transactionId;

    @NotNull
    @Column(name = "amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal amount;

    @NotNull
    @Column(name = "tax_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal taxAmount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private TransactionStatus status = TransactionStatus.PENDING;

    /**
     * Relasi pertama ke User: siapa yang MENGINPUT transaksi ini (Persona 1 -
     * Staf Proyek pada PRD Bab 2.3). Nama kolom FK dibuat eksplisit berbeda
     * dari relasi approvedBy di bawah agar Hibernate tidak ambigu menentukan
     * join column, karena keduanya menunjuk ke entity User yang sama.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    /**
     * Relasi kedua ke User: siapa yang MENYETUJUI transaksi ini (Persona 2 -
     * Manajer Keuangan pada PRD Bab 2.3). Nullable karena transaksi yang
     * masih berstatus PENDING belum memiliki approver.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BudgetsTransaction)) {
            return false;
        }
        return transactionId != null && transactionId.equals(((BudgetsTransaction) o).transactionId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "BudgetsTransaction{" +
            "transactionId=" +
            transactionId +
            ", amount=" +
            amount +
            ", status=" +
            status +
            "}"
        );
    }
}
