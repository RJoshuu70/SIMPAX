package id.ac.upnvj.simpax.service;

import id.ac.upnvj.simpax.domain.StockPriceCache;
import id.ac.upnvj.simpax.repository.StockPriceCacheRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service StockPriceCache - implementasi cache-aside pattern yang sudah
 * dijelaskan rasionalnya di Javadoc entity StockPriceCache.
 *
 * <p>STATUS SAAT INI - PENTING UNTUK LAPORAN AKADEMIK: method
 * fetchFromExternalProvider() di bawah ini adalah MOCK/SIMULASI, BUKAN
 * panggilan API eksternal sungguhan. Tidak ada API key Alpha Vantage/
 * Finnhub yang dikonfigurasi di .env saat ini. Harga yang dihasilkan
 * bersifat deterministik (selalu sama untuk ticker yang sama, dihitung
 * dari hash MD5 ticker) - ini SENGAJA, bukan random, agar hasil demo
 * dapat direproduksi dan tidak membingungkan saat dosen menguji ulang
 * dengan ticker yang sama di waktu berbeda.
 *
 * <p>Jika integrasi API eksternal sungguhan ingin diimplementasikan,
 * yang perlu diubah HANYA isi method fetchFromExternalProvider() -
 * seluruh logic cache-aside (TTL check, simpan ke cache, dsb) di
 * getCurrentPrice() tidak perlu disentuh. Ini adalah manfaat konkret dari
 * memisahkan "cara mendapatkan data" dari "kapan harus mendapatkan data
 * baru" sebagai dua tanggung jawab berbeda - prinsip Single Responsibility.
 */
@Service
public class StockPriceCacheService {

    /**
     * TTL cache 15 menit. Untuk PoC akademik, nilai ini cukup untuk
     * menyeimbangkan antara data yang "cukup segar" untuk demo dan
     * menghindari rate limit API eksternal (lihat Javadoc entity
     * StockPriceCache soal rate limit free tier 25 request/hari).
     */
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);

    private final StockPriceCacheRepository priceCacheRepo;

    public StockPriceCacheService(StockPriceCacheRepository priceCacheRepo) {
        this.priceCacheRepo = priceCacheRepo;
    }

    @Transactional
    public BigDecimal getCurrentPrice(String tickerSymbol, String exchange) {
        StockPriceCache cached = priceCacheRepo.findById(tickerSymbol).orElse(null);

        boolean cacheValid = cached != null &&
            Duration.between(cached.getFetchedAt(), Instant.now()).compareTo(CACHE_TTL) < 0;

        if (cacheValid) {
            return cached.getLastPrice();
        }

        BigDecimal freshPrice = fetchFromExternalProvider(tickerSymbol);

        StockPriceCache toSave = cached != null ? cached : new StockPriceCache();
        toSave.setTickerSymbol(tickerSymbol);
        toSave.setExchange(exchange);
        toSave.setLastPrice(freshPrice);
        toSave.setFetchedAt(Instant.now());
        priceCacheRepo.save(toSave);

        return freshPrice;
    }

    /**
     * MOCK provider - lihat catatan kelas di atas. Menghasilkan harga
     * deterministik antara 1.000 - 100.000 berdasarkan hash ticker, supaya
     * terlihat "masuk akal" sebagai harga saham IDX tanpa harus memanggil
     * API eksternal sungguhan.
     */
    private BigDecimal fetchFromExternalProvider(String tickerSymbol) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(tickerSymbol.getBytes());
            long seed = Math.abs(((hash[0] & 0xFF) << 8) | (hash[1] & 0xFF));
            BigDecimal mockPrice = BigDecimal.valueOf(1000 + (seed % 99000));
            return mockPrice.setScale(2, RoundingMode.HALF_UP);
        } catch (NoSuchAlgorithmException e) {
            // MD5 dijamin tersedia di setiap JVM standar - blok ini secara
            // praktis tidak akan pernah tereksekusi, tapi tetap wajib
            // ditangani karena checked exception pada API MessageDigest.
            return new BigDecimal("1000.00");
        }
    }
}
