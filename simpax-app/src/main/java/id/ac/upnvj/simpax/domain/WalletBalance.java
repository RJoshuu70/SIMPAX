package id.ac.upnvj.simpax.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity WalletBalance - modul tambahan di luar PRD v1.0, untuk kebutuhan
 * "saldo" sesuai pembagian tugas tim (Joshua - Backend Developer).
 *
 * <p>Keputusan desain: entity ini SENGAJA dipisah dari BudgetsTransaction
 * walaupun keduanya tentang "uang". BudgetsTransaction adalah arus kas
 * PERUSAHAAN dengan flow approval (SoD antara Staf dan Manajer).
 * WalletBalance adalah saldo kas milik INDIVIDUAL user untuk keperluan
 * portofolio investasi pribadi (lihat StockHolding). Mencampur keduanya
 * dalam satu tabel akan membuat audit trail ambigu - sulit dibedakan mana
 * baris yang representasi kas perusahaan dan mana yang representasi saldo
 * pribadi user saat proses audit oleh Persona 3 (Auditor).
 */
@Entity
@Table(name = "wallet_balances")
@Getter
@Setter
@NoArgsConstructor
public class WalletBalance implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "wallet_id")
    private UUID walletId;

    /**
     * Satu User dapat memiliki lebih dari satu WalletBalance (multi-currency),
     * sehingga relasi ManyToOne (bukan OneToOne) dipilih secara sengaja.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @Column(name = "cash_balance", precision = 18, scale = 2, nullable = false)
    private BigDecimal cashBalance = BigDecimal.ZERO;

    @NotNull
    @Size(max = 10)
    @Column(name = "currency", length = 10, nullable = false)
    private String currency = "IDR";

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WalletBalance)) {
            return false;
        }
        return walletId != null && walletId.equals(((WalletBalance) o).walletId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "WalletBalance{" + "walletId=" + walletId + ", cashBalance=" + cashBalance + ", currency='" + currency + "'" + "}";
    }
}
