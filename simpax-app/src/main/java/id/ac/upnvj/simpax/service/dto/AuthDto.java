package id.ac.upnvj.simpax.service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO untuk login/register - menggunakan Java Records (Java 16+).
 * Records adalah immutable data carrier yang ideal untuk DTO karena:
 * - Compiler otomatis generate constructor, getter, equals, hashCode, toString
 * - Immutable: DTO tidak boleh dimodifikasi setelah dibuat (data kontrak)
 */
public class AuthDto {

    public record LoginRequest(
        @NotBlank(message = "Username tidak boleh kosong")
        String username,

        @NotBlank(message = "Password tidak boleh kosong")
        String password
    ) {}

    public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 100)
        String username,

        @NotBlank @Email
        String email,

        @NotBlank @Size(min = 8, message = "Password minimal 8 karakter")
        String password,

        @NotBlank(message = "Role harus diisi")
        String roleName
    ) {}

    /**
     * Response login yang dikembalikan ke client.
     * SENGAJA tidak mengembalikan seluruh data User (hanya field esensial)
     * karena prinsip data minimization - client hanya perlu tahu apa yang
     * benar-benar dibutuhkan untuk menampilkan UI awal pasca-login.
     */
    public record LoginResponse(
        String token,
        String username,
        String role,
        long expiresInMinutes
    ) {}

    public record RegisterResponse(
        String userId,
        String username,
        String email,
        String role
    ) {}
}
