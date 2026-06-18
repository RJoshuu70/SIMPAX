package id.ac.upnvj.simpax.security.jwt;

import java.io.IOException;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.PublicKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Menyediakan PrivateKey dan PublicKey sebagai Spring Bean, dibaca SEKALI
 * saat aplikasi startup (bukan setiap request) untuk efisiensi - operasi
 * baca file & parsing RSA key relatif mahal jika dilakukan berulang.
 *
 * <p>Jika file key belum ada di path yang dikonfigurasi, aplikasi akan
 * GAGAL START dengan pesan error yang jelas (fail-fast), daripada baru
 * gagal saat ada user pertama yang mencoba login - prinsip ini penting
 * agar masalah konfigurasi terdeteksi sedini mungkin, idealnya sebelum
 * aplikasi sempat di-deploy untuk demo.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtKeyConfig {

    @Bean
    public PrivateKey jwtPrivateKey(JwtProperties jwtProperties) throws IOException {
        return RsaKeyLoader.loadPrivateKey(Path.of(jwtProperties.privateKeyPath()));
    }

    @Bean
    public PublicKey jwtPublicKey(JwtProperties jwtProperties) throws IOException {
        return RsaKeyLoader.loadPublicKey(Path.of(jwtProperties.publicKeyPath()));
    }
}
