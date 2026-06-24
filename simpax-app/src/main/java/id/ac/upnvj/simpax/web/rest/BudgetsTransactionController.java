package id.ac.upnvj.simpax.web.rest;

import id.ac.upnvj.simpax.security.FortressRbacService;
import id.ac.upnvj.simpax.service.BudgetsTransactionService;
import id.ac.upnvj.simpax.service.dto.BudgetsTransactionDto.CreateRequest;
import id.ac.upnvj.simpax.service.dto.BudgetsTransactionDto.TransactionResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller transaksi budget.
 *
 * <p>Perhatikan penggunaan Principal (bukan @RequestBody untuk field userId):
 * username diambil dari SecurityContext (yang sudah diisi oleh
 * JwtAuthenticationFilter berdasarkan token yang valid), bukan dari
 * request body. Ini mencegah client memalsukan "atas nama siapa" transaksi
 * dibuat - prinsip "trust the token, not the input".
 *
 * <p>Setiap endpoint kini melewati RBAC check via FortressRbacService yang
 * mengquery role dari OpenLDAP (Apache Fortress PoC). Ini membuktikan
 * integrasi tiga lapis: APISIX (autentikasi JWT) -> Spring Security
 * (ekstrak role, lihat JwtAuthenticationFilter) -> Fortress/OpenLDAP
 * (otorisasi granular berbasis RBAC matrix pada PRD).
 *
 * <p>PENTING: authority yang disimpan JwtAuthenticationFilter ke
 * SecurityContext berformat "ROLE_&lt;NAMA_ROLE&gt;" (konvensi Spring
 * Security, contoh: "ROLE_STAFF"). FortressRbacService.checkAccess()
 * mengharapkan nama role polos ("STAFF") karena itulah representasi yang
 * tersimpan sebagai cn di OpenLDAP. Karena itu prefix "ROLE_" WAJIB
 * dihapus di extractRole() sebelum role diteruskan ke rbacService -
 * tanpa ini, setiap RBAC check akan gagal cocok dan seluruh request akan
 * ditolak dengan 403 meskipun role-nya valid.
 */
@RestController
@RequestMapping("/api/transactions")
public class BudgetsTransactionController {

    private static final String ROLE_PREFIX = "ROLE_";

    private final BudgetsTransactionService transactionService;
    private final FortressRbacService rbacService;

    public BudgetsTransactionController(
            BudgetsTransactionService transactionService,
            FortressRbacService rbacService) {
        this.transactionService = transactionService;
        this.rbacService = rbacService;
    }

    /**
     * Ambil nama role polos (tanpa prefix "ROLE_") dari Authentication.
     */
    private String extractRole(Authentication auth) {
        String authority = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .findFirst()
            .orElse("UNKNOWN");
        return authority.startsWith(ROLE_PREFIX)
            ? authority.substring(ROLE_PREFIX.length())
            : authority;
    }

    @PostMapping
    public ResponseEntity<?> create(
        @Valid @RequestBody CreateRequest request,
        Principal principal,
        Authentication auth
    ) {
        String role = extractRole(auth);
        if (!rbacService.checkAccess(role, "CREATE", "TRANSACTION")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Akses ditolak: role " + role + " tidak boleh membuat transaksi");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(transactionService.createTransaction(request, principal.getName()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(
        @PathVariable UUID id,
        Principal principal,
        Authentication auth
    ) {
        String role = extractRole(auth);
        if (!rbacService.checkAccess(role, "APPROVE", "TRANSACTION")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Akses ditolak: role " + role + " tidak boleh melakukan approval");
        }
        return ResponseEntity.ok(transactionService.approveTransaction(id, principal.getName()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(
        @PathVariable UUID id,
        Principal principal,
        Authentication auth
    ) {
        String role = extractRole(auth);
        if (!rbacService.checkAccess(role, "APPROVE", "TRANSACTION")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Akses ditolak: role " + role + " tidak boleh melakukan reject");
        }
        return ResponseEntity.ok(transactionService.rejectTransaction(id, principal.getName()));
    }

    @GetMapping("/pending")
    public ResponseEntity<?> getPending(Authentication auth) {
        String role = extractRole(auth);
        if (!rbacService.checkAccess(role, "READ", "TRANSACTION")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Akses ditolak: role " + role + " tidak boleh membaca transaksi pending");
        }
        return ResponseEntity.ok(transactionService.getPendingTransactions());
    }

    @GetMapping("/mine")
    public ResponseEntity<?> getMine(Principal principal, Authentication auth) {
        String role = extractRole(auth);
        if (!rbacService.checkAccess(role, "READ", "TRANSACTION")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Akses ditolak: role " + role + " tidak boleh membaca transaksi");
        }
        return ResponseEntity.ok(transactionService.getMyTransactions(principal.getName()));
    }
}