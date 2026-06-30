package id.ac.upnvj.simpax.service;

import id.ac.upnvj.simpax.domain.User;
import id.ac.upnvj.simpax.domain.WalletBalance;
import id.ac.upnvj.simpax.repository.UserRepository;
import id.ac.upnvj.simpax.repository.WalletBalanceRepository;
import id.ac.upnvj.simpax.service.dto.WalletBalanceDto.AmountRequest;
import id.ac.upnvj.simpax.service.dto.WalletBalanceDto.CreateWalletRequest;
import id.ac.upnvj.simpax.service.dto.WalletBalanceDto.WalletResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service WalletBalance - saldo kas INDIVIDUAL milik satu user.
 *
 * <p>KEPUTUSAN ARSITEKTUR PENTING (beda model otorisasi dari
 * BudgetsTransactionService): modul ini TIDAK memakai RBAC matrix
 * (FortressRbacService/role hasAnyRole) seperti modul transaksi budget
 * perusahaan. Alasannya konseptual, bukan teknis: BudgetsTransaction
 * adalah arus kas PERUSAHAAN dengan struktur persetujuan berjenjang
 * (Staf membuat, Manajer menyetujui) sehingga otorisasi berbasis ROLE
 * memang tepat. WalletBalance adalah dompet PRIBADI - setiap user, apa
 * pun role-nya (STAFF, MANAGER, AUDITOR, sekalipun DIREKSI), berhak penuh
 * atas dompetnya sendiri dan TIDAK berhak atas dompet user lain.
 *
 * <p>Karena itu, model otorisasi yang tepat di sini adalah
 * "ownership-based authorization": yang diperiksa bukan "role apa yang
 * login", melainkan "apakah baris data ini benar-benar milik orang yang
 * login". Implementasinya: setiap operasi pada wallet tertentu (deposit,
 * withdraw) memverifikasi wallet.getUser().getUserId() sama dengan userId
 * dari token JWT, dan melempar AccessDeniedException (otomatis diterjemah-
 * kan Spring Security menjadi HTTP 403) jika tidak cocok.
 *
 * <p>Catatan desain lain: constraint "satu currency per user" ditegakkan
 * di level service (lihat createWallet), bukan UNIQUE constraint di
 * database, karena skema Liquibase saat ini belum mendefinisikannya.
 * Ini secara sengaja didokumentasikan sebagai technical debt yang sudah
 * diketahui - solusi idealnya adalah menambah UNIQUE(user_id, currency)
 * pada migration berikutnya, bukan menambal terus-menerus di service.
 */
@Service
public class WalletBalanceService {

    private final WalletBalanceRepository walletRepo;
    private final UserRepository userRepository;

    public WalletBalanceService(WalletBalanceRepository walletRepo, UserRepository userRepository) {
        this.walletRepo = walletRepo;
        this.userRepository = userRepository;
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public WalletResponse createWallet(CreateWalletRequest request, String currentUsername) {
        User owner = findUserOrThrow(currentUsername);
        String currency = request.currency().toUpperCase();

        walletRepo.findByUserUserIdAndCurrency(owner.getUserId(), currency).ifPresent(existing -> {
            throw new IllegalArgumentException(
                "Wallet dengan currency " + currency + " sudah ada untuk user ini"
            );
        });

        WalletBalance wallet = new WalletBalance();
        wallet.setUser(owner);
        wallet.setCurrency(currency);
        wallet.setUpdatedAt(Instant.now());
        // cashBalance sengaja TIDAK di-set dari request - default 0 dari
        // entity (BigDecimal.ZERO) tetap berlaku, sesuai komentar di DTO.

        return toResponse(walletRepo.save(wallet));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<WalletResponse> getMyWallets(String currentUsername) {
        User owner = findUserOrThrow(currentUsername);
        return walletRepo.findByUserUserId(owner.getUserId()).stream().map(this::toResponse).toList();
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public WalletResponse deposit(UUID walletId, AmountRequest request, String currentUsername) {
        WalletBalance wallet = findOwnedWalletOrThrow(walletId, currentUsername);
        wallet.setCashBalance(wallet.getCashBalance().add(request.amount()));
        wallet.setUpdatedAt(Instant.now());
        return toResponse(walletRepo.save(wallet));
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public WalletResponse withdraw(UUID walletId, AmountRequest request, String currentUsername) {
        WalletBalance wallet = findOwnedWalletOrThrow(walletId, currentUsername);
        if (wallet.getCashBalance().compareTo(request.amount()) < 0) {
            throw new IllegalArgumentException(
                "Saldo tidak cukup: saldo saat ini " + wallet.getCashBalance() + ", penarikan diminta " + request.amount()
            );
        }
        wallet.setCashBalance(wallet.getCashBalance().subtract(request.amount()));
        wallet.setUpdatedAt(Instant.now());
        return toResponse(walletRepo.save(wallet));
    }

    /**
     * Dipanggil juga oleh StockHoldingService saat eksekusi buy/sell saham,
     * karena perpindahan saldo akibat transaksi saham TIDAK boleh melalui
     * jalur deposit/withdraw publik di atas (yang menerima input amount
     * bebas dari client) - melainkan dihitung otomatis dari price * quantity
     * di sisi server. Method ini package-private secara konsep (dipanggil
     * antar service dalam modul yang sama), TIDAK diekspos lewat Controller.
     */
    @Transactional
    WalletBalance adjustBalanceForTrade(UUID walletId, java.math.BigDecimal delta) {
        WalletBalance wallet = walletRepo.findById(walletId)
            .orElseThrow(() -> new IllegalArgumentException("Wallet tidak ditemukan: " + walletId));
        java.math.BigDecimal newBalance = wallet.getCashBalance().add(delta);
        if (newBalance.signum() < 0) {
            throw new IllegalArgumentException(
                "Saldo tidak cukup untuk transaksi ini: saldo saat ini " + wallet.getCashBalance()
            );
        }
        wallet.setCashBalance(newBalance);
        wallet.setUpdatedAt(Instant.now());
        return walletRepo.save(wallet);
    }

    WalletBalance findOrCreateIdrWallet(User owner) {
        return walletRepo.findByUserUserIdAndCurrency(owner.getUserId(), "IDR").orElseGet(() -> {
            WalletBalance wallet = new WalletBalance();
            wallet.setUser(owner);
            wallet.setCurrency("IDR");
            wallet.setUpdatedAt(Instant.now());
            return walletRepo.save(wallet);
        });
    }

    private WalletBalance findOwnedWalletOrThrow(UUID walletId, String currentUsername) {
        User currentUser = findUserOrThrow(currentUsername);
        WalletBalance wallet = walletRepo.findById(walletId)
            .orElseThrow(() -> new IllegalArgumentException("Wallet tidak ditemukan: " + walletId));

        if (!wallet.getUser().getUserId().equals(currentUser.getUserId())) {
            throw new AccessDeniedException("Wallet ini bukan milik user yang sedang login");
        }
        return wallet;
    }

    private User findUserOrThrow(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User tidak ditemukan: " + username));
    }

    private WalletResponse toResponse(WalletBalance wallet) {
        return new WalletResponse(
            wallet.getWalletId(),
            wallet.getCashBalance(),
            wallet.getCurrency(),
            wallet.getUpdatedAt()
        );
    }
}
