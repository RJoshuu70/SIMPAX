package id.ac.upnvj.simpax.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity StockPriceCache - tabel cache harga saham terakhir yang berhasil
 * diambil dari API eksternal (contoh: Alpha Vantage, Finnhub - free tier).
 *
 * <p>RASIONAL ARSITEKTUR (penting dipahami, bukan sekadar tabel biasa):
 * API saham gratis pada umumnya memiliki rate limit yang sangat ketat
 * (misal 25 request/hari untuk Alpha Vantage free tier). Tanpa caching,
 * risiko nyata adalah sistem menampilkan error "429 Too Many Requests"
 * tepat saat demo UAS di depan dosen.
 *
 * <p>Strategi mitigasi: setiap kali backend berhasil fetch harga saham dari
 * API eksternal, hasilnya disimpan di tabel ini beserta timestamp
 * (fetchedAt). Service layer (lihat StockPriceService - akan dibuat pada
 * tahap modul bisnis) akan menerapkan logika "gunakan cache jika usianya
 * masih di bawah TTL tertentu, baru panggil API eksternal jika cache basi
 * atau belum ada". Pola ini disebut cache-aside pattern.
 *
 * <p>Primary key pada entity ini adalah tickerSymbol itu sendiri (bukan UUID
 * terpisah) karena secara konseptual hanya boleh ada SATU baris cache
 * teraktual per kombinasi ticker+exchange - tidak ada kebutuhan menyimpan
 * histori harga di tabel ini (histori harga, jika dibutuhkan nanti, akan
 * menjadi tabel terpisah agar tabel cache ini tetap ringan/cepat dibaca).
 */
@Entity
@Table(name = "stock_price_caches")
@Getter
@Setter
@NoArgsConstructor
public class StockPriceCache implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @NotNull
    @Size(max = 10)
    @Column(name = "ticker_symbol", length = 10, nullable = false)
    private String tickerSymbol;

    @NotNull
    @Size(max = 20)
    @Column(name = "exchange", length = 20, nullable = false)
    private String exchange = "IDX";

    @NotNull
    @Column(name = "last_price", precision = 18, scale = 2, nullable = false)
    private BigDecimal lastPrice;

    @NotNull
    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StockPriceCache)) {
            return false;
        }
        return tickerSymbol != null && tickerSymbol.equals(((StockPriceCache) o).tickerSymbol);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "StockPriceCache{" +
            "tickerSymbol='" +
            tickerSymbol +
            "'" +
            ", lastPrice=" +
            lastPrice +
            ", fetchedAt=" +
            fetchedAt +
            "}"
        );
    }
}
