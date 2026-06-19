package id.ac.upnvj.simpax;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point aplikasi backend SIMPAX.
 *
 * <p>Tidak ada konfigurasi tambahan yang sengaja ditaruh di sini - mengikuti
 * prinsip "thin main class". Seluruh konfigurasi (security, JWT, CORS, dll)
 * didefinisikan sebagai @Configuration class terpisah di package config/
 * dan security/, agar class ini tetap mudah dibaca sebagai titik masuk
 * murni tanpa logic tersembunyi.
 */
@SpringBootApplication(exclude = { org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class })
public class SimpaxApp {

    public static void main(String[] args) {
        SpringApplication.run(SimpaxApp.class, args);
    }
}
