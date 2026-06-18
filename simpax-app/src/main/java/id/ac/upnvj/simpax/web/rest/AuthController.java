package id.ac.upnvj.simpax.web.rest;

import id.ac.upnvj.simpax.service.AuthenticationService;
import id.ac.upnvj.simpax.service.dto.AuthDto.LoginRequest;
import id.ac.upnvj.simpax.service.dto.AuthDto.LoginResponse;
import id.ac.upnvj.simpax.service.dto.AuthDto.RegisterRequest;
import id.ac.upnvj.simpax.service.dto.AuthDto.RegisterResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller untuk endpoint autentikasi (publik, tidak perlu JWT).
 *
 * <p>Endpoint ini adalah titik masuk Layer 7 untuk flow autentikasi:
 * Client → APISIX (rate-limit saja, tidak require JWT) → Controller ini
 * → AuthenticationService → Syncope → Database → TokenProvider → JWT Response
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authService;

    public AuthController(AuthenticationService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
