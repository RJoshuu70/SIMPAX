package id.ac.upnvj.simpax.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import java.util.Map;

/**
 * Implementasi IdentityProviderClient yang memanggil Apache Syncope REST API.
 *
 * <p>STATUS: Sudah diverifikasi end-to-end (register + login) terhadap
 * Apache Syncope 4.1.1 yang berjalan di docker-compose - lihat
 * docs/diagrams/kontrak_integrasi_syncope.md untuk catatan verifikasi.
 *
 * Endpoint Syncope yang dipakai:
 *
 * 1. Validasi kredensial (login):
 *    POST http://{SYNCOPE_HOST}:{SYNCOPE_PORT}/syncope/rest/accessTokens/login
 *    Header: Authorization: Basic base64(username:password)
 *    Response sukses: 200 OK dengan body berisi access token Syncope
 *    Response gagal: 401 Unauthorized
 *
 * 2. Registrasi user baru:
 *    POST http://{SYNCOPE_HOST}:{SYNCOPE_PORT}/syncope/rest/users?storePassword=true
 *    Header: Authorization: Basic base64(SYNCOPE_ADMIN:password)
 *    Body: JSON UserCR Syncope - field discriminator "_class" (underscore,
 *    BUKAN "@class") sesuai skema resmi di GET /syncope/rest/openapi.json
 *    Response sukses: 201 Created dengan body berisi user.key (syncopeRefId)
 *
 * <p>Konfigurasi yang dibutuhkan (sudah ada di application-dev.yml /
 * application-docker.yml dan diteruskan via env var di docker-compose.yml):
 *   simpax.syncope.base-url
 *   simpax.syncope.admin-user
 *   simpax.syncope.admin-password
 */
@Component
public class SyncopeIdentityProviderClient implements IdentityProviderClient {

    private static final Logger log = LoggerFactory.getLogger(SyncopeIdentityProviderClient.class);

    private final RestClient restClient;
    private final String adminUser;
    private final String adminPassword;

    public SyncopeIdentityProviderClient(
        @Value("${simpax.syncope.base-url:http://localhost:8081}") String baseUrl,
        @Value("${simpax.syncope.admin-user:admin}") String adminUser,
        @Value("${simpax.syncope.admin-password:password}") String adminPassword
    ) {
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;
        // RestClient adalah HTTP client modern Spring 6, pengganti RestTemplate.
        // Instance ini dikonfigurasi dengan baseUrl Syncope agar setiap call
        // tidak perlu menulis URL penuh berulang kali.
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Override
    public boolean validateCredentials(String username, String password) {
        try {
            // Syncope REST API menggunakan HTTP Basic Auth untuk endpoint login.
            // Jika response 200: kredensial valid.
            // Jika response 401: username/password salah.
            restClient.post()
                .uri("/syncope/rest/accessTokens/login")
                .header(HttpHeaders.AUTHORIZATION, basicAuth(username, password))
                .retrieve()
                .toBodilessEntity();

            log.debug("Validasi Syncope berhasil untuk user: {}", username);
            return true;

        } catch (HttpClientErrorException.Unauthorized e) {
            log.debug("Validasi Syncope gagal untuk user: {} - kredensial tidak valid", username);
            return false;

        } catch (Exception e) {
            log.error("Syncope tidak dapat dihubungi: {}", e.getMessage());
            throw new IdentityProviderException("Identity Provider tidak dapat dihubungi. Coba beberapa saat lagi.", e);
        }
    }

    @Override
    public String registerUser(String username, String email, String password) {
        try {
            // Struktur request body sesuai skema UserCR Syncope 4.1.1 (sudah
            // dikonfirmasi via GET /syncope/rest/openapi.json di environment ini).
            // Field "_class" (underscore, BUKAN "@class") wajib ada untuk
            // polymorphic deserialization - field ini "required" di schema UserCR.
           var userPayload = Map.of(
                 "_class", "org.apache.syncope.common.lib.request.UserCR",
                 "username", username,
                 "password", password,
                 "realm", "/",
                 "plainAttrs", java.util.List.of(
                   Map.of("schema", "email", "values", java.util.List.of(email))
                  )
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                .uri("/syncope/rest/users?storePassword=true")
                .header(HttpHeaders.AUTHORIZATION, basicAuth(adminUser, adminPassword))
                .body(userPayload)
                .retrieve()
                .body(Map.class);

            if (response == null || !response.containsKey("entity")) {
                throw new IdentityProviderException("Syncope tidak mengembalikan key untuk user baru", null);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> entity = (Map<String, Object>) response.get("entity");
            if (entity == null || !entity.containsKey("key")) {
                throw new IdentityProviderException("Syncope tidak mengembalikan key untuk user baru", null);
            }

            String syncopeKey = entity.get("key").toString();

            log.info("User '{}' berhasil diregistrasi ke Syncope dengan key: {}", username, syncopeKey);
            return syncopeKey;

        } catch (IdentityProviderException e) {
            throw e;
        } catch (Exception e) {
            log.error("Gagal meregistrasi user ke Syncope: {}", e.getMessage());
            throw new IdentityProviderException("Gagal meregistrasi user ke Identity Provider.", e);
        }
    }

    private String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
    }
}