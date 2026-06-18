package id.ac.upnvj.simpax.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity StockHolding - representasi kepemilikan saham seorang User.
 *
 * <p>Field tickerSymbol mendukung format kode emiten Bursa Efek Indonesia
 * (IDX), contoh "BBCA.JK" atau "TLKM.JK" - panjang maksimum 10 karakter
 * sudah mencakup 4 huruf kode emiten + ".JK" (total 7 karakter) dengan
 * buffer untuk format exchange lain yang sedikit lebih panjang.
 *
 * <p>Field exchange ditambahkan terpisah dari tickerSymbol (bukan
 * digabung jadi satu string) supaya query/filter berdasarkan bursa
 * tertentu tidak perlu parsing string - prinsip atomicity pada
 * desain kolom database.
 */
@Entity
@Table(name = "stock_holdings")
@Getter
@Setter
@NoArgsConstructor
public class StockHolding implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "holding_id")
    private UUID holdingId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @Size(max = 10)
    @Pattern(
        regexp = "^[A-Z0-9]{1,6}(\\.[A-Z]{1,3})?$",
        message = "Format ticker tidak valid. Contoh yang benar: BBCA.JK, TLKM.JK"
    )
    @Column(name = "ticker_symbol", length = 10, nullable = false)
    private String tickerSymbol;

    @NotNull
    @Size(max = 20)
    @Column(name = "exchange", length = 20, nullable = false)
    private String exchange = "IDX";

    /**
     * Presisi 4 digit desimal (bukan 2 seperti kolom uang) karena kuantitas
     * saham dapat berupa pecahan lot pada beberapa skema trading fraksional.
     */
    @NotNull
    @Column(name = "quantity", precision = 18, scale = 4, nullable = false)
    private BigDecimal quantity;

    @NotNull
    @Column(name = "avg_buy_price", precision = 18, scale = 2, nullable = false)
    private BigDecimal avgBuyPrice;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StockHolding)) {
            return false;
        }
        return holdingId != null && holdingId.equals(((StockHolding) o).holdingId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "StockHolding{" +
            "holdingId=" +
            holdingId +
            ", tickerSymbol='" +
            tickerSymbol +
            "'" +
            ", quantity=" +
            quantity +
            "}"
        );
    }
}
