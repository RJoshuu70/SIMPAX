package id.ac.upnvj.simpax.config;

import id.ac.upnvj.simpax.security.jwt.JwtAuthenticationFilter;
import id.ac.upnvj.simpax.security.jwt.TokenProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Konfigurasi Spring Security untuk backend SIMPAX.
 *
 * <p>Arsitektur keamanan yang diterapkan di sini memetakan langsung ke
 * layer OSI yang menjadi fokus PRD:
 *
 * <ul>
 *   <li>Layer 6 (Presentation) - dikontrol oleh HTTPS/TLS yang dikonfigurasi
 *       Zaskia pada APISIX, bukan di sini. Backend ini di-deploy di belakang
 *       APISIX sehingga koneksi APISIX → Backend bisa HTTP lokal.</li>
 *   <li>Layer 7 (Application) - dikontrol oleh konfigurasi ini: autentikasi
 *       JWT, otorisasi berbasis role (RBAC), CORS, dan session management.</li>
 * </ul>
 *
 * <p>Keputusan kunci: STATELESS session. Backend ini tidak menyimpan
 * session di server sama sekali - sesuai prinsip Zero Trust dan memudahkan
 * horizontal scaling jika nanti dibutuhkan. Seluruh identitas user dibawa
 * oleh JWT pada setiap request.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final TokenProvider tokenProvider;

    public SecurityConfig(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF dinonaktifkan karena backend ini adalah pure REST API yang
            // dikonsumsi oleh React frontend (bukan form HTML tradisional) dan
            // sudah dilindungi oleh JWT - CSRF protection relevan hanya untuk
            // session-based auth dengan cookie, bukan JWT-based.
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Stateless: Spring Security tidak membuat HttpSession apapun,
            // tidak ada session tracking di server.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Rule otorisasi per endpoint - disusun dari yang PALING SPESIFIK
            // di atas ke yang PALING UMUM di bawah. Urutan ini penting:
            // Spring Security mengevaluasi rule dari atas ke bawah dan berhenti
            // pada rule pertama yang cocok.
            .authorizeHttpRequests(auth ->
                auth
                    // Endpoint publik - tidak perlu token (login, register, public key)
                    .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                    .requestMatchers("/api/public/**").permitAll()
                    .requestMatchers("/.well-known/jwks.json").permitAll()

                    // Actuator health/info untuk monitoring Regina (HertzBeat/SkyWalking)
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                    // Endpoint khusus AUDITOR dan DIREKSI
                    .requestMatchers("/api/admin/**").hasAnyRole("AUDITOR", "DIREKSI")

                    // Endpoint approval transaksi - hanya MANAGER dan DIREKSI
                    .requestMatchers(HttpMethod.POST, "/api/transactions/*/approve").hasAnyRole("MANAGER", "DIREKSI")
                    .requestMatchers(HttpMethod.POST, "/api/transactions/*/reject").hasAnyRole("MANAGER", "DIREKSI")

                    // Semua endpoint lain: harus ter-autentikasi (punya JWT valid)
                    .anyRequest().authenticated()
            )

            // Daftarkan JwtAuthenticationFilter SEBELUM filter default Spring Security
            // yang mencoba autentikasi via username/password form (yang tidak relevan
            // untuk arsitektur JWT ini).
            .addFilterBefore(
                new JwtAuthenticationFilter(tokenProvider),
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }

    /**
     * CORS configuration.
     * Saat development, React dev server berjalan di port 3000 sementara
     * backend di 8080 - tanpa CORS config ini, browser akan memblokir
     * request dari frontend ke backend karena dianggap cross-origin.
     *
     * PENTING untuk Zaskia: saat APISIX sudah terpasang di depan backend,
     * CORS sebaiknya dikonfigurasi HANYA di APISIX (bukan di keduanya)
     * untuk menghindari duplikasi header yang bisa menyebabkan browser error
     * "multiple values in Access-Control-Allow-Origin header".
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
            "http://localhost:3000",
            "http://localhost:9080"  // Port default APISIX HTTP
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
