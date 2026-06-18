package id.ac.upnvj.simpax.security.jwt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Utility untuk membaca keypair RSA dari file .pem pada filesystem.
 *
 * <p>KEPUTUSAN DESAIN: keypair ini di-generate SEKALI secara manual lewat
 * OpenSSL (lihat scripts/generate-jwt-keypair.sh) dan disimpan sebagai file
 * di luar repository (path-nya saja yang dikonfigurasi, file aslinya masuk
 * .gitignore). Pendekatan ini lebih sederhana untuk kebutuhan PoC akademik
 * dibanding mengelola keypair lewat KeyStore (.jks) yang sintaksnya lebih
 * rumit, namun TETAP mengikuti prinsip RS256 asymmetric signing yang
 * disyaratkan PRD Bab 11.3.
 *
 * <p>Public key dari file ini nantinya akan diserahkan secara terpisah
 * (bukan lewat Git) kepada Zaskia untuk dikonfigurasikan pada plugin
 * jwt-auth Apache APISIX, agar APISIX dapat memverifikasi keaslian token
 * tanpa pernah memegang private key.
 */
public final class RsaKeyLoader {

    private RsaKeyLoader() {
        // Utility class - mencegah instansiasi yang tidak perlu.
    }

    public static PrivateKey loadPrivateKey(Path pemFilePath) throws IOException {
        String pem = Files.readString(pemFilePath);
        String cleaned = stripPemHeaders(pem, "PRIVATE KEY");
        byte[] decoded = Base64.getDecoder().decode(cleaned);

        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(
                "Gagal memuat RSA private key dari " + pemFilePath + ". " + "Pastikan file dalam format PKCS#8 PEM (lihat scripts/generate-jwt-keypair.sh).",
                e
            );
        }
    }

    public static PublicKey loadPublicKey(Path pemFilePath) throws IOException {
        String pem = Files.readString(pemFilePath);
        String cleaned = stripPemHeaders(pem, "PUBLIC KEY");
        byte[] decoded = Base64.getDecoder().decode(cleaned);

        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Gagal memuat RSA public key dari " + pemFilePath + ".", e);
        }
    }

    private static String stripPemHeaders(String pem, String label) {
        return pem.replace("-----BEGIN " + label + "-----", "").replace("-----END " + label + "-----", "").replaceAll("\\s", "");
    }
}
