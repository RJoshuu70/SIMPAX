package id.ac.upnvj.simpax.repository;

import id.ac.upnvj.simpax.domain.StockHolding;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockHoldingRepository extends JpaRepository<StockHolding, UUID> {
    /**
     * Dipakai untuk menampilkan seluruh portofolio saham milik satu user
     * (halaman "Portofolio Saya").
     */
    List<StockHolding> findByUserUserId(UUID userId);

    /**
     * Dipakai saat user melakukan transaksi BELI/JUAL saham yang sama:
     * jika user sudah memegang ticker tersebut, kita UPDATE baris yang ada
     * (rata-ratakan avgBuyPrice & tambah/kurangi quantity) daripada membuat
     * baris baru - mencegah duplikasi data kepemilikan per ticker per user.
     */
    Optional<StockHolding> findByUserUserIdAndTickerSymbol(UUID userId, String tickerSymbol);
}
