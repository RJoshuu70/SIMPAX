package id.ac.upnvj.simpax.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binding type-safe terhadap konfigurasi "simpax.security.jwt.*" pada
 * application-dev.yml / application-prod.yml.
 *
 * <p>Menggunakan @ConfigurationProperties (bukan @Value berulang di banyak
 * tempat) supaya seluruh konfigurasi JWT terkumpul dalam satu kelas yang
 * type-safe dan mudah di-mock saat unit testing TokenProvider.
 */
@ConfigurationProperties(prefix = "simpax.security.jwt")
public record JwtProperties(String privateKeyPath, String publicKeyPath, long tokenValidityInMinutes, String issuer) {}
