package id.ac.upnvj.simpax.repository;

import id.ac.upnvj.simpax.domain.StockPriceCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockPriceCacheRepository extends JpaRepository<StockPriceCache, String> {
    // Primary key entity ini ADALAH tickerSymbol (String), sehingga
    // findById() bawaan JpaRepository sudah cukup untuk kebutuhan lookup
    // cache-aside pattern - tidak perlu derived query method tambahan.
}
