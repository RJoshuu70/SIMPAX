package id.ac.upnvj.simpax.service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class StockHoldingDto {

    /**
     * Pattern validasi ticker SENGAJA disalin identik dari entity
     * StockHolding (bukan dipanggil ulang lewat referensi statis ke
     * entity) - DTO lapisan presentasi tidak boleh bergantung langsung
     * pada constraint internal entity, supaya perubahan skema database
     * di masa depan tidak otomatis mengubah kontrak API publik tanpa
     * disadari. Trade-off-nya adalah duplikasi kecil yang harus dijaga
     * konsisten manual - dianggap sepadan untuk PoC skala ini.
     */
    private static final String TICKER_PATTERN = "^[A-Z0-9]{1,6}(\\.[A-Z]{1,3})?$";

    public record BuyRequest(
        @NotBlank(message = "Ticker symbol tidak boleh kosong")
        @Size(max = 10)
        @Pattern(regexp = TICKER_PATTERN, message = "Format ticker tidak valid. Contoh: BBCA.JK")
        String tickerSymbol,

        @Size(max = 20)
        String exchange,

        @NotNull(message = "Quantity tidak boleh kosong")
        @DecimalMin(value = "0.0001", message = "Quantity harus lebih dari 0")
        BigDecimal quantity
    ) {}

    public record SellRequest(
        @NotBlank(message = "Ticker symbol tidak boleh kosong")
        @Size(max = 10)
        @Pattern(regexp = TICKER_PATTERN, message = "Format ticker tidak valid. Contoh: BBCA.JK")
        String tickerSymbol,

        @NotNull(message = "Quantity tidak boleh kosong")
        @DecimalMin(value = "0.0001", message = "Quantity harus lebih dari 0")
        BigDecimal quantity
    ) {}

    public record HoldingResponse(
        UUID holdingId,
        String tickerSymbol,
        String exchange,
        BigDecimal quantity,
        BigDecimal avgBuyPrice,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl,
        Instant createdAt
    ) {}

    /**
     * Hasil eksekusi BUY/SELL - menggabungkan info holding terbaru DAN
     * saldo wallet setelah transaksi dalam satu response, supaya frontend
     * (Tahap 6 berikutnya) tidak perlu memanggil dua endpoint terpisah
     * untuk menampilkan hasil satu aksi "beli saham". realizedPnl bernilai
     * null pada hasil BUY (belum ada realisasi untung/rugi saat membeli),
     * dan terisi pada hasil SELL.
     */
    public record TradeResult(
        HoldingResponse holding,
        BigDecimal walletCashBalanceAfter,
        BigDecimal realizedPnl
    ) {}
}
