package id.ac.upnvj.simpax.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter JWT yang dieksekusi SEKALI per request (extends OncePerRequestFilter),
 * berjalan sebelum controller menerima request.
 *
 * <p>Posisi filter ini dalam arsitektur:
 * [Client] → [APISIX: verifikasi JWT & rate limit] → [Backend: filter ini]
 *
 * <p>Pertanyaan yang wajar: "Kalau APISIX sudah verifikasi JWT, mengapa
 * backend perlu verifikasi lagi?"
 * Jawaban: defense in depth. Saat development lokal, request ke backend
 * TIDAK melewati APISIX. Selain itu, desain yang mengandalkan satu titik
 * validasi (hanya APISIX) bertentangan dengan prinsip Zero Trust - sistem
 * tidak boleh berasumsi bahwa request yang datang ke backend pasti sudah
 * divalidasi oleh lapisan sebelumnya.
 *
 * <p>Flow filter ini:
 * 1. Ekstrak token dari header Authorization: Bearer {token}
 * 2. Validasi signature & expiry via TokenProvider
 * 3. Jika valid: isi SecurityContext dengan Authentication object
 *    (berisi username + authorities/role) - Spring Security akan
 *    menggunakan ini untuk keputusan otorisasi di endpoint
 * 4. Jika tidak valid: biarkan SecurityContext kosong - Spring Security
 *    akan otomatis menolak request dengan 401 pada endpoint yang dilindungi
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenProvider tokenProvider;

    public JwtAuthenticationFilter(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String token = extractToken(request);

        if (StringUtils.hasText(token) && tokenProvider.validateToken(token)) {
            String username = tokenProvider.getUsernameFromToken(token);
            String role = tokenProvider.getRoleFromToken(token);

            // Prefix "ROLE_" adalah konvensi Spring Security untuk authority
            // yang mewakili role - dibutuhkan agar @PreAuthorize("hasRole('MANAGER')")
            // dapat bekerja dengan benar di level controller/service nanti.
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

            var authentication = new UsernamePasswordAuthenticationToken(username, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("Autentikasi JWT berhasil untuk user: {}, role: {}", username, role);
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
