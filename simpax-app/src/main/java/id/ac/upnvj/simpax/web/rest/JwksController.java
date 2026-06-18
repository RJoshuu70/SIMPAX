package id.ac.upnvj.simpax.web.rest;

import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * JWKS (JSON Web Key Set) Endpoint.
 *
 * <p>Endpoint ini mengekspos public key RSA milik backend dalam format JWKS
 * yang merupakan standar RFC 7517. Dengan endpoint ini, Zaskia tidak perlu
 * meng-copy-paste file public.pem secara manual ke konfigurasi APISIX -
 * APISIX dapat dikonfigurasi untuk fetch public key secara otomatis dari
 * URL: http://simpax-backend:8080/.well-known/jwks.json
 *
 * <p>Endpoint ini SENGAJA dibuat public (tanpa autentikasi - lihat
 * SecurityConfig) karena public key memang BOLEH diketahui siapapun
 * secara definisi asymmetric cryptography. Public key hanya dapat dipakai
 * untuk VERIFIKASI, tidak untuk membuat token baru.
 *
 * <p>FORMAT OUTPUT yang akan diterima APISIX (contoh nyata):
 * {
 *   "keys": [{
 *     "kty": "RSA",
 *     "use": "sig",
 *     "alg": "RS256",
 *     "n": "..base64url encoded modulus..",
 *     "e": "AQAB"
 *   }]
 * }
 */
@RestController
public class JwksController {

    private final PublicKey publicKey;

    public JwksController(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getJwks() {
        RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;

        // Base64URL encoding (bukan Base64 biasa) adalah standar JWKS - RFC 7517.
        // Perbedaan: Base64URL menggunakan '-' dan '_' sebagai ganti '+' dan '/'
        // agar aman dipakai dalam URL tanpa perlu percent-encoding.
        Base64.Encoder base64Url = Base64.getUrlEncoder().withoutPadding();

        Map<String, Object> jwk = Map.of(
            "kty", "RSA",
            "use", "sig",
            "alg", "RS256",
            "n", base64Url.encodeToString(rsaPublicKey.getModulus().toByteArray()),
            "e", base64Url.encodeToString(rsaPublicKey.getPublicExponent().toByteArray())
        );

        return Map.of("keys", List.of(jwk));
    }
}
