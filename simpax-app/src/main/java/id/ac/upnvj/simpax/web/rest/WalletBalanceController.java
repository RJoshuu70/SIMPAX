package id.ac.upnvj.simpax.web.rest;

import id.ac.upnvj.simpax.service.WalletBalanceService;
import id.ac.upnvj.simpax.service.dto.WalletBalanceDto.AmountRequest;
import id.ac.upnvj.simpax.service.dto.WalletBalanceDto.CreateWalletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller WalletBalance - dompet kas pribadi user.
 *
 * <p>Berbeda dari BudgetsTransactionController, controller ini TIDAK
 * melakukan RBAC check via FortressRbacService - lihat Javadoc
 * WalletBalanceService untuk alasan arsitekturnya (ownership-based,
 * bukan role-based). Username diambil dari Principal (hasil parsing JWT
 * oleh JwtAuthenticationFilter), tidak pernah dari path/body, supaya
 * client tidak bisa mengklaim wallet milik orang lain hanya dengan
 * mengganti walletId di URL - pengecekan kepemilikan tetap dilakukan di
 * service sebagai pertahanan lapis kedua (defense in depth).
 */
@RestController
@RequestMapping("/api/wallets")
public class WalletBalanceController {

    private final WalletBalanceService walletService;

    public WalletBalanceController(WalletBalanceService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateWalletRequest request, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(walletService.createWallet(request, principal.getName()));
    }

    @GetMapping("/mine")
    public ResponseEntity<?> getMine(Principal principal) {
        return ResponseEntity.ok(walletService.getMyWallets(principal.getName()));
    }

    @PostMapping("/{id}/deposit")
    public ResponseEntity<?> deposit(
        @PathVariable UUID id,
        @Valid @RequestBody AmountRequest request,
        Principal principal
    ) {
        return ResponseEntity.ok(walletService.deposit(id, request, principal.getName()));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<?> withdraw(
        @PathVariable UUID id,
        @Valid @RequestBody AmountRequest request,
        Principal principal
    ) {
        return ResponseEntity.ok(walletService.withdraw(id, request, principal.getName()));
    }
}
