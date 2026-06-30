package id.ac.upnvj.simpax.web.rest.errors;

import id.ac.upnvj.simpax.security.IdentityProviderClient.IdentityProviderException;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Penanganan exception terpusat via @RestControllerAdvice.
 *
 * <p>Keputusan keamanan penting: TIDAK ADA stack trace atau pesan error
 * internal yang dikembalikan ke client dalam response body. Hanya pesan
 * yang sudah kita definisikan secara eksplisit yang boleh keluar.
 * Stack trace tetap di-log di server (untuk debugging) tapi tidak
 * dikirim ke client (mencegah information disclosure - OWASP A05).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String firstError = e.getBindingResult().getFieldErrors()
            .stream()
            .findFirst()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .orElse("Input tidak valid");
        return errorResponse(HttpStatus.BAD_REQUEST, firstError);
    }

    @ExceptionHandler(IdentityProviderException.class)
    public ResponseEntity<Map<String, Object>> handleIdentityProvider(IdentityProviderException e) {
        log.error("Identity Provider error: {}", e.getMessage(), e);
        return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    /**
     * PENTING: handler ini WAJIB ada secara eksplisit, terpisah dari
     * catch-all Exception.class di bawah. Tanpa ini, AccessDeniedException
     * (dilempar misalnya oleh WalletBalanceService saat user mencoba akses
     * wallet milik orang lain) akan tertangkap oleh handler generik
     * Exception.class dan keliru dikembalikan sebagai 500 Internal Server
     * Error - padahal secara semantik HTTP ini seharusnya 403 Forbidden.
     * Spring memilih @ExceptionHandler berdasarkan exception type PALING
     * SPESIFIK yang cocok, jadi method ini otomatis diprioritaskan
     * dibanding handleGeneral() untuk exception jenis ini - tidak perlu
     * urutan deklarasi khusus.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        return errorResponse(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        // Pesan generik ke client - tidak boleh ekspos detail internal
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Terjadi kesalahan pada server. Silakan coba beberapa saat lagi.");
    }

    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
            "status", status.value(),
            "error", status.getReasonPhrase(),
            "message", message,
            "timestamp", Instant.now().toString()
        ));
    }
}
