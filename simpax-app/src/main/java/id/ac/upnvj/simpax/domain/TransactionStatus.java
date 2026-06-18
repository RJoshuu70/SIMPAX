package id.ac.upnvj.simpax.domain;

/**
 * Enum status transaksi, membatasi nilai yang valid sesuai PRD Bab 10.4
 * ("PENDING, APPROVED, atau REJECTED").
 *
 * <p>Keputusan desain: PRD mendefinisikan kolom status sebagai VARCHAR(20)
 * pada level database. Namun pada level kode Java, kita TIDAK membiarkan
 * field ini berupa String bebas - kita pakai Java enum dengan
 * {@code @Enumerated(EnumType.STRING)} pada Entity. Alasannya:
 *
 * <ul>
 *   <li>Type-safety saat compile time: typo seperti "APROVED" akan langsung
 *       ditolak compiler, bukan baru diketahui saat runtime atau setelah
 *       data salah masuk ke database.</li>
 *   <li>IDE autocomplete dan refactoring aman saat status ini dipakai di
 *       banyak tempat (Service, Controller, test).</li>
 *   <li>EnumType.STRING (bukan ORDINAL) dipilih agar nilai yang tersimpan
 *       di database tetap berupa teks ("PENDING") bukan angka indeks (0),
 *       sehingga tetap human-readable saat di-inspect langsung di psql -
 *       penting untuk kebutuhan audit oleh Persona 3 (Auditor).</li>
 * </ul>
 */
public enum TransactionStatus {
    PENDING,
    APPROVED,
    REJECTED,
}
