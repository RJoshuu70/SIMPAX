package id.ac.upnvj.simpax.repository;

import id.ac.upnvj.simpax.domain.BudgetsTransaction;
import id.ac.upnvj.simpax.domain.TransactionStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BudgetsTransactionRepository extends JpaRepository<BudgetsTransaction, UUID> {
    /**
     * Dipakai Manajer Keuangan (Persona 2) untuk melihat daftar transaksi
     * yang menunggu approval mereka - kebutuhan utama dashboard approval
     * sesuai PRD Bab 5 (Dasbor Arus Kas dan Laba Rugi Real-Time).
     */
    List<BudgetsTransaction> findByStatus(TransactionStatus status);

    /**
     * Dipakai untuk menampilkan riwayat transaksi yang diinput oleh Staf
     * tertentu (misal pada halaman "transaksi saya").
     */
    List<BudgetsTransaction> findByCreatedByUserId(UUID createdByUserId);

    /**
     * Dipakai pada modul Audit Trail (Persona 3 - Auditor) untuk menelusuri
     * seluruh transaksi yang sudah pernah disetujui/ditolak oleh Manajer
     * tertentu - relevan untuk investigasi kepatuhan SoD.
     */
    List<BudgetsTransaction> findByApprovedByUserId(UUID approvedByUserId);
}
