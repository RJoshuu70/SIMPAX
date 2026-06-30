package id.ac.upnvj.simpax.repository;

import id.ac.upnvj.simpax.domain.WalletBalance;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletBalanceRepository extends JpaRepository<WalletBalance, UUID> {
    /**
     * Nama method "findByUserUserId" sengaja mengikuti konvensi Spring Data
     * JPA untuk "nested property" - karena WalletBalance.user adalah objek
     * User (bukan UUID langsung), dan kita ingin filter berdasarkan
     * User.userId, penulisannya menjadi gabungan "User" + "UserId".
     */
    List<WalletBalance> findByUserUserId(UUID userId);

    /**
     * Dipakai saat createWallet() untuk mencegah satu user memiliki lebih
     * dari satu wallet dengan currency yang sama (constraint logis di level
     * service, karena tidak ada UNIQUE constraint komposit di skema
     * Liquibase saat ini - lihat catatan di WalletBalanceService).
     */
    Optional<WalletBalance> findByUserUserIdAndCurrency(UUID userId, String currency);
}
