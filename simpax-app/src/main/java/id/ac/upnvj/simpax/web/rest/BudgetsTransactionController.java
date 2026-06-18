package id.ac.upnvj.simpax.web.rest;

import id.ac.upnvj.simpax.service.BudgetsTransactionService;
import id.ac.upnvj.simpax.service.dto.BudgetsTransactionDto.CreateRequest;
import id.ac.upnvj.simpax.service.dto.BudgetsTransactionDto.TransactionResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
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
 * Controller transaksi budget.
 *
 * <p>Perhatikan penggunaan Principal (bukan @RequestBody untuk field userId):
 * username diambil dari SecurityContext (yang sudah diisi oleh
 * JwtAuthenticationFilter berdasarkan token yang valid), bukan dari
 * request body. Ini mencegah client memalsukan "atas nama siapa" transaksi
 * dibuat - prinsip "trust the token, not the input".
 */
@RestController
@RequestMapping("/api/transactions")
public class BudgetsTransactionController {

    private final BudgetsTransactionService transactionService;

    public BudgetsTransactionController(BudgetsTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(
        @Valid @RequestBody CreateRequest request,
        Principal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(transactionService.createTransaction(request, principal.getName()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<TransactionResponse> approve(
        @PathVariable UUID id,
        Principal principal
    ) {
        return ResponseEntity.ok(transactionService.approveTransaction(id, principal.getName()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<TransactionResponse> reject(
        @PathVariable UUID id,
        Principal principal
    ) {
        return ResponseEntity.ok(transactionService.rejectTransaction(id, principal.getName()));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<TransactionResponse>> getPending() {
        return ResponseEntity.ok(transactionService.getPendingTransactions());
    }

    @GetMapping("/mine")
    public ResponseEntity<List<TransactionResponse>> getMine(Principal principal) {
        return ResponseEntity.ok(transactionService.getMyTransactions(principal.getName()));
    }
}
