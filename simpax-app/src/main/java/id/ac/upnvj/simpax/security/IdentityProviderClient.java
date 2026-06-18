package id.ac.upnvj.simpax.security;

/**
 * Abstraksi kontrak komunikasi dengan Identity Provider (Apache Syncope).
 *
 * <p>Mengapa interface, bukan langsung implementasi Syncope?
 * Prinsip Dependency Inversion (huruf D dalam SOLID): kode tingkat tinggi
 * (AuthenticationService) tidak boleh bergantung pada implementasi konkret
 * (kelas SyncopeIdentityProviderClient), melainkan pada abstraksi ini.
 *
 * <p>Manfaat praktis untuk tim ini:
 * - Saat Syncope Zaskia belum ready, AuthenticationService tetap bisa
 *   dikembangkan dan di-test dengan implementasi mock/stub.
 * - Jika nanti tim memutuskan ganti ke Keycloak, cukup buat implementasi
 *   baru tanpa mengubah AuthenticationService.
 */
public interface IdentityProviderClient {

    /**
     * Memvalidasi kredensial user ke Identity Provider.
     *
     * @param username username yang diinput user saat login
     * @param password password plaintext (harus dikirim lewat HTTPS/TLS)
     * @return true jika kredensial valid, false jika tidak
     * @throws IdentityProviderException jika Syncope tidak dapat dihubungi
     *         (bukan karena password salah, tapi karena koneksi/service down)
     */
    boolean validateCredentials(String username, String password);

    /**
     * Meregistrasi user baru ke Identity Provider. Dipanggil saat register,
     * sebelum atau bersamaan dengan penyimpanan User ke database lokal.
     *
     * @return syncopeRefId - ID unik yang diberikan Syncope untuk user ini,
     *         akan disimpan di kolom syncope_ref_id pada tabel app_user
     */
    String registerUser(String username, String email, String password);

    class IdentityProviderException extends RuntimeException {
        public IdentityProviderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
