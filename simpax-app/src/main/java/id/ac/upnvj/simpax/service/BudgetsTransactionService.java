package id.ac.upnvj.simpax.service;

import id.ac.upnvj.simpax.domain.BudgetsTransaction;
import id.ac.upnvj.simpax.domain.TransactionStatus;
import id.ac.upnvj.simpax.domain.User;
import id.ac.upnvj.simpax.repository.BudgetsTransactionRepository;
import id.ac.upnvj.simpax.repository.UserRepository;
import id.ac.upnvj.simpax.service.dto.BudgetsTransactionDto.CreateRequest;
import id.ac.upnvj.simpax.service.dto.BudgetsTransactionDto.TransactionResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetsTransactionService {

    /**
     * PPN rate Indonesia: 11% per PMK No. 63/PMK.03/2022.
     * BigDecimal dipakai untuk kalkulasi keuangan (bukan double/float)
     * karena floating point tidak bisa merepresentasikan nilai desimal
     * secara akurat - relevan untuk audit keuangan.
     */
    private static final BigDecimal PPN_RATE = new BigDecimal("0.11");

    private final BudgetsTransactionRepository transactionRepo;
    private final UserRepository userRepository;

    public BudgetsTransactionService(
        BudgetsTransactionRepository transactionRepo,
        UserRepository userRepository
    ) {
        this.transactionRepo = transactionRepo;
        this.userRepository = userRepository;
    }

    /**
     * STAFF dan MANAGER/DIREKSI bisa membuat transaksi.
     * currentUsername diambil dari SecurityContext oleh controller
     * (bukan dari request body - mencegah user memalsukan createdBy).
     */
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public TransactionResponse createTransaction(CreateRequest request, String currentUsername) {
        User creator = userRepository.findByUsername(currentUsername)
            .orElseThrow(() -> new IllegalArgumentException("User tidak ditemukan: " + currentUsername));

        BigDecimal taxAmount = request.amount()
            .multiply(PPN_RATE)
            .setScale(2, RoundingMode.HALF_UP);

        BudgetsTransaction tx = new BudgetsTransaction();
        tx.setAmount(request.amount());
        tx.setTaxAmount(taxAmount);
        tx.setStatus(TransactionStatus.PENDING);
        tx.setCreatedBy(creator);
        tx.setCreatedAt(Instant.now());

        return toResponse(transactionRepo.save(tx));
    }

    /**
     * Approval - hanya MANAGER dan DIREKSI (enforced juga di SecurityConfig).
     * Di sinilah validasi SoD (Separation of Duties) dilakukan pada level
     * aplikasi - sebagai pelengkap CHECK constraint di level database.
     */
    @Transactional
    @PreAuthorize("hasAnyRole('MANAGER','DIREKSI')")
    public TransactionResponse approveTransaction(UUID transactionId, String approverUsername) {
        BudgetsTransaction tx = findPendingOrThrow(transactionId);
        User approver = userRepository.findByUsername(approverUsername)
            .orElseThrow(() -> new IllegalArgumentException("User approver tidak ditemukan"));

        // SoD enforcement di level service - validasi bahwa approver bukan
        // orang yang sama dengan yang membuat transaksi ini
        if (tx.getCreatedBy().getUserId().equals(approver.getUserId())) {
            throw new IllegalArgumentException(
                "Pelanggaran Separation of Duties: user yang membuat transaksi tidak dapat menyetujui transaksi yang sama"
            );
        }

        tx.setStatus(TransactionStatus.APPROVED);
        tx.setApprovedBy(approver);
        tx.setApprovedAt(Instant.now());

        return toResponse(transactionRepo.save(tx));
    }

    @Transactional
    @PreAuthorize("hasAnyRole('MANAGER','DIREKSI')")
    public TransactionResponse rejectTransaction(UUID transactionId, String approverUsername) {
        BudgetsTransaction tx = findPendingOrThrow(transactionId);
        User approver = userRepository.findByUsername(approverUsername)
            .orElseThrow(() -> new IllegalArgumentException("User approver tidak ditemukan"));

        if (tx.getCreatedBy().getUserId().equals(approver.getUserId())) {
            throw new IllegalArgumentException("Pelanggaran SoD: user yang membuat transaksi tidak dapat menolak transaksi yang sama");
        }

        tx.setStatus(TransactionStatus.REJECTED);
        tx.setApprovedBy(approver);
        tx.setApprovedAt(Instant.now());

        return toResponse(transactionRepo.save(tx));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('MANAGER','DIREKSI','AUDITOR')")
    public List<TransactionResponse> getPendingTransactions() {
        return transactionRepo.findByStatus(TransactionStatus.PENDING)
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<TransactionResponse> getMyTransactions(String currentUsername) {
        User user = userRepository.findByUsername(currentUsername)
            .orElseThrow(() -> new IllegalArgumentException("User tidak ditemukan"));
        return transactionRepo.findByCreatedByUserId(user.getUserId())
            .stream().map(this::toResponse).toList();
    }

    private BudgetsTransaction findPendingOrThrow(UUID id) {
        BudgetsTransaction tx = transactionRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Transaksi tidak ditemukan: " + id));
        if (tx.getStatus() != TransactionStatus.PENDING) {
            throw new IllegalArgumentException(
                "Transaksi sudah berstatus " + tx.getStatus() + " dan tidak dapat diubah lagi"
            );
        }
        return tx;
    }

    private TransactionResponse toResponse(BudgetsTransaction tx) {
        return new TransactionResponse(
            tx.getTransactionId(),
            tx.getAmount(),
            tx.getTaxAmount(),
            tx.getAmount().add(tx.getTaxAmount()),
            tx.getStatus(),
            tx.getCreatedBy() != null ? tx.getCreatedBy().getUsername() : null,
            tx.getApprovedBy() != null ? tx.getApprovedBy().getUsername() : null,
            tx.getCreatedAt(),
            tx.getApprovedAt()
        );
    }
}
