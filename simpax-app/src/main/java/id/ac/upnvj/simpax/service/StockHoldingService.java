package id.ac.upnvj.simpax.service;

import id.ac.upnvj.simpax.domain.StockHolding;
import id.ac.upnvj.simpax.domain.User;
import id.ac.upnvj.simpax.domain.WalletBalance;
import id.ac.upnvj.simpax.repository.StockHoldingRepository;
import id.ac.upnvj.simpax.repository.UserRepository;
import id.ac.upnvj.simpax.service.dto.StockHoldingDto.BuyRequest;
import id.ac.upnvj.simpax.service.dto.StockHoldingDto.HoldingResponse;
import id.ac.upnvj.simpax.service.dto.StockHoldingDto.SellRequest;
import id.ac.upnvj.simpax.service.dto.StockHoldingDto.TradeResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service StockHolding - portofolio saham pribadi user, terintegrasi
 * dengan WalletBalanceService untuk eksekusi BUY/SELL.
 *
 * <p>KEPUTUSAN ARSITEKTUR PALING PENTING DI MODUL INI: method buy() dan
 * sell() melibatkan PERUBAHAN PADA DUA AGGREGATE SEKALIGUS - StockHolding
 * (kepemilikan saham bertambah/berkurang) DAN WalletBalance (saldo kas
 * berkurang/bertambah). Kedua perubahan ini WAJIB konsisten sebagai satu
 * unit (ACID) - tidak boleh ada kondisi di mana saldo sudah terpotong
 * tapi kepemilikan saham gagal tercatat, atau sebaliknya.
 *
 * <p>Cara memastikan konsistensi ini BUKAN dengan mencoba "rollback manual"
 * (misalnya try-catch yang mengembalikan saldo secara manual jika langkah
 * kedua gagal) - pendekatan itu rapuh dan mudah punya celah race condition.
 * Solusi yang benar adalah membungkus seluruh method dalam SATU
 * @Transactional Spring: jika exception apa pun dilempar di mana pun di
 * dalam method (termasuk dari WalletBalanceService.adjustBalanceForTrade),
 * Spring akan me-rollback SELURUH transaksi database secara otomatis -
 * baik perubahan StockHolding maupun WalletBalance, sehingga tidak pernah
 * ada state "separuh jalan" yang tersimpan permanen.
 */
@Service
public class StockHoldingService {

    private final StockHoldingRepository holdingRepo;
    private final UserRepository userRepository;
    private final WalletBalanceService walletService;
    private final StockPriceCacheService priceCacheService;

    public StockHoldingService(
        StockHoldingRepository holdingRepo,
        UserRepository userRepository,
        WalletBalanceService walletService,
        StockPriceCacheService priceCacheService
    ) {
        this.holdingRepo = holdingRepo;
        this.userRepository = userRepository;
        this.walletService = walletService;
        this.priceCacheService = priceCacheService;
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public TradeResult buy(BuyRequest request, String currentUsername) {
        User buyer = findUserOrThrow(currentUsername);
        String ticker = request.tickerSymbol().toUpperCase();
        String exchange = (request.exchange() == null || request.exchange().isBlank())
            ? "IDX"
            : request.exchange().toUpperCase();

        BigDecimal currentPrice = priceCacheService.getCurrentPrice(ticker, exchange);
        BigDecimal totalCost = currentPrice.multiply(request.quantity()).setScale(2, RoundingMode.HALF_UP);

        // Saldo kas dipotong dari wallet IDR milik buyer. Untuk PoC, semua
        // transaksi saham diasumsikan dalam mata uang IDR - asumsi ini
        // wajar untuk konteks saham IDX, dan disebutkan secara eksplisit
        // di sini agar tidak jadi asumsi tersembunyi yang membingungkan
        // pembaca kode lain di kemudian hari.
        WalletBalance idrWallet = walletService.findOrCreateIdrWallet(buyer);
        walletService.adjustBalanceForTrade(idrWallet.getWalletId(), totalCost.negate());

        StockHolding holding = holdingRepo
            .findByUserUserIdAndTickerSymbol(buyer.getUserId(), ticker)
            .orElseGet(() -> {
                StockHolding fresh = new StockHolding();
                fresh.setUser(buyer);
                fresh.setTickerSymbol(ticker);
                fresh.setExchange(exchange);
                fresh.setQuantity(BigDecimal.ZERO);
                fresh.setAvgBuyPrice(BigDecimal.ZERO);
                fresh.setCreatedAt(Instant.now());
                return fresh;
            });

        // Weighted average cost: avgBuyPrice baru dihitung sebagai rata-rata
        // tertimbang antara posisi lama dan pembelian baru, BUKAN sekadar
        // ditimpa dengan harga beli terbaru - ini metode akuntansi standar
        // (average cost method) untuk menghitung basis biaya kepemilikan
        // saham yang dibeli bertahap pada harga berbeda-beda.
        BigDecimal oldQuantity = holding.getQuantity();
        BigDecimal oldAvgPrice = holding.getAvgBuyPrice();
        BigDecimal newQuantity = oldQuantity.add(request.quantity());
        BigDecimal newAvgPrice = oldQuantity
            .multiply(oldAvgPrice)
            .add(request.quantity().multiply(currentPrice))
            .divide(newQuantity, 2, RoundingMode.HALF_UP);

        holding.setQuantity(newQuantity);
        holding.setAvgBuyPrice(newAvgPrice);

        StockHolding saved = holdingRepo.save(holding);
        WalletBalance walletAfter = walletService.findOrCreateIdrWallet(buyer);

        return new TradeResult(toResponse(saved, currentPrice), walletAfter.getCashBalance(), null);
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public TradeResult sell(SellRequest request, String currentUsername) {
        User seller = findUserOrThrow(currentUsername);
        String ticker = request.tickerSymbol().toUpperCase();

        StockHolding holding = holdingRepo
            .findByUserUserIdAndTickerSymbol(seller.getUserId(), ticker)
            .orElseThrow(() -> new IllegalArgumentException(
                "Anda tidak memiliki saham " + ticker + " untuk dijual"
            ));

        if (holding.getQuantity().compareTo(request.quantity()) < 0) {
            throw new IllegalArgumentException(
                "Quantity jual (" + request.quantity() + ") melebihi kepemilikan saat ini (" + holding.getQuantity() + ")"
            );
        }

        BigDecimal currentPrice = priceCacheService.getCurrentPrice(ticker, holding.getExchange());
        BigDecimal proceeds = currentPrice.multiply(request.quantity()).setScale(2, RoundingMode.HALF_UP);

        // Realized P/L dihitung terhadap avgBuyPrice SEBELUM quantity
        // dikurangi - basis biaya (avgBuyPrice) sendiri TIDAK berubah saat
        // sell (berbeda dari buy yang merata-ratakan ulang), karena metode
        // average cost menghitung P/L dari selisih harga jual terhadap
        // basis biaya yang sudah ditetapkan saat pembelian terakhir kali.
        BigDecimal realizedPnl = currentPrice
            .subtract(holding.getAvgBuyPrice())
            .multiply(request.quantity())
            .setScale(2, RoundingMode.HALF_UP);

        WalletBalance idrWallet = walletService.findOrCreateIdrWallet(seller);
        walletService.adjustBalanceForTrade(idrWallet.getWalletId(), proceeds);

        BigDecimal remainingQuantity = holding.getQuantity().subtract(request.quantity());
        holding.setQuantity(remainingQuantity);

        StockHolding saved;
        if (remainingQuantity.signum() == 0) {
            // Quantity habis -> hapus baris holding (bukan disimpan dengan
            // quantity=0) supaya query "portofolio saya" tidak menampilkan
            // ticker yang sudah tidak dimiliki sama sekali.
            holdingRepo.delete(holding);
            saved = holding;
        } else {
            saved = holdingRepo.save(holding);
        }

        WalletBalance walletAfter = walletService.findOrCreateIdrWallet(seller);
        return new TradeResult(toResponse(saved, currentPrice), walletAfter.getCashBalance(), realizedPnl);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<HoldingResponse> getPortfolio(String currentUsername) {
        User owner = findUserOrThrow(currentUsername);
        return holdingRepo.findByUserUserId(owner.getUserId()).stream()
            .map(h -> toResponse(h, priceCacheService.getCurrentPrice(h.getTickerSymbol(), h.getExchange())))
            .toList();
    }

    private User findUserOrThrow(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User tidak ditemukan: " + username));
    }

    private HoldingResponse toResponse(StockHolding holding, BigDecimal currentPrice) {
        BigDecimal marketValue = currentPrice.multiply(holding.getQuantity()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal unrealizedPnl = currentPrice
            .subtract(holding.getAvgBuyPrice())
            .multiply(holding.getQuantity())
            .setScale(2, RoundingMode.HALF_UP);

        return new HoldingResponse(
            holding.getHoldingId(),
            holding.getTickerSymbol(),
            holding.getExchange(),
            holding.getQuantity(),
            holding.getAvgBuyPrice(),
            currentPrice,
            marketValue,
            unrealizedPnl,
            holding.getCreatedAt()
        );
    }
}
