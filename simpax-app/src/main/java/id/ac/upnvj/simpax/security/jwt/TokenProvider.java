package id.ac.upnvj.simpax.security.jwt;

import id.ac.upnvj.simpax.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * TokenProvider - bertanggung jawab MENERBITKAN dan MEMVALIDASI JWT RS256.
 *
 * <p>Sesuai keputusan tim: backend Spring Boot ini adalah pihak yang berhak
 * men-generate token (karena memegang private key). Apache APISIX milik
 * Zaskia hanya melakukan verifikasi independen menggunakan public key -
 * backend ini TIDAK BERTANGGUNG JAWAB atas konfigurasi verifikasi di sisi
 * APISIX, namun method validateToken() di bawah tetap disediakan agar
 * backend ini juga bisa melakukan validasi mandiri jika suatu request
 * sampai ke backend tanpa melalui APISIX (misal saat development lokal
 * sebelum APISIX terpasang).
 *
 * <p>Claims yang disisipkan ke dalam token (lihat method generateToken):
 * - sub (subject): username
 * - userId: UUID user, dipakai backend untuk lookup cepat tanpa query ulang
 * - role: nama role (STAFF/MANAGER/AUDITOR/DIREKSI) - akan dipakai APISIX
 *   maupun Spring Security untuk keputusan otorisasi (RBAC).
 */
@Component
public class TokenProvider {

    private static final Logger log = LoggerFactory.getLogger(TokenProvider.class);
    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_ROLE = "role";

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final JwtProperties jwtProperties;

    public TokenProvider(PrivateKey privateKey, PublicKey publicKey, JwtProperties jwtProperties) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.jwtProperties = jwtProperties;
    }

    /**
     * Menerbitkan JWT baru setelah proses login berhasil (password sudah
     * divalidasi oleh Apache Syncope sebelum method ini dipanggil - lihat
     * AuthenticationService).
     */
    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtProperties.tokenValidityInMinutes(), ChronoUnit.MINUTES);

        return Jwts.builder()
            .subject(user.getUsername())
            .claim(CLAIM_USER_ID, user.getUserId().toString())
            .claim(CLAIM_ROLE, user.getRole().getRoleName())
            .issuer(jwtProperties.issuer())
            .issuedAt(java.util.Date.from(now))
            .expiration(java.util.Date.from(expiry))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact();
    }

    /**
     * Memvalidasi keaslian & masa berlaku token. Mengembalikan true jika
     * valid, false jika tidak (termasuk jika signature tidak cocok, token
     * sudah expired, atau format token rusak).
     *
     * <p>SENGAJA tidak melempar exception ke caller untuk kasus token tidak
     * valid - caller (lihat JwtAuthenticationFilter) hanya perlu tahu
     * "valid atau tidak", bukan detail teknis exception, untuk mencegah
     * informasi internal bocor lewat pesan error ke client.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Validasi JWT gagal: {}", e.getMessage());
            return false;
        }
    }

    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token).getPayload();
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public String getRoleFromToken(String token) {
        return parseClaims(token).get(CLAIM_ROLE, String.class);
    }
}
