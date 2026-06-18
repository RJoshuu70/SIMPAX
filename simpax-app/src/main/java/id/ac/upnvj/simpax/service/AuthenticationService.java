package id.ac.upnvj.simpax.service;

import id.ac.upnvj.simpax.domain.Role;
import id.ac.upnvj.simpax.domain.User;
import id.ac.upnvj.simpax.repository.RoleRepository;
import id.ac.upnvj.simpax.repository.UserRepository;
import id.ac.upnvj.simpax.security.IdentityProviderClient;
import id.ac.upnvj.simpax.security.jwt.JwtProperties;
import id.ac.upnvj.simpax.security.jwt.TokenProvider;
import id.ac.upnvj.simpax.service.dto.AuthDto.LoginRequest;
import id.ac.upnvj.simpax.service.dto.AuthDto.LoginResponse;
import id.ac.upnvj.simpax.service.dto.AuthDto.RegisterRequest;
import id.ac.upnvj.simpax.service.dto.AuthDto.RegisterResponse;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service login dan register - ini "direktur orkestra" yang mengoordinasikan:
 * Syncope (validasi identitas) + UserRepository (data lokal) + TokenProvider (JWT).
 *
 * <p>Anotasi @Transactional pada method yang menulis ke database memastikan:
 * jika register berhasil di Syncope tapi gagal saat INSERT ke database lokal,
 * seluruh operasi otomatis di-rollback (tidak ada user "setengah jadi" yang
 * sudah terdaftar di Syncope tapi tidak ada di tabel app_user).
 */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final IdentityProviderClient identityProviderClient;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TokenProvider tokenProvider;
    private final JwtProperties jwtProperties;

    public AuthenticationService(
        IdentityProviderClient identityProviderClient,
        UserRepository userRepository,
        RoleRepository roleRepository,
        TokenProvider tokenProvider,
        JwtProperties jwtProperties
    ) {
        this.identityProviderClient = identityProviderClient;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tokenProvider = tokenProvider;
        this.jwtProperties = jwtProperties;
    }

    /**
     * Flow login:
     * 1. Pastikan user ada di database lokal (cek username)
     * 2. Validasi password ke Syncope
     * 3. Update lastLoginAt di database lokal
     * 4. Generate JWT RS256 dan kembalikan ke client
     *
     * <p>Mengapa cek database lokal DULU, baru Syncope?
     * Efisiensi: jika username tidak ada di database lokal, tidak ada
     * gunanya memanggil Syncope (network round-trip yang mahal). Selain itu,
     * jika user memang tidak ada di kedua tempat, respons "username atau
     * password salah" tetap konsisten - tidak membocorkan informasi apakah
     * username-nya yang salah atau password-nya.
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
            .filter(User::getIsActive)
            .orElseThrow(() -> new IllegalArgumentException("Username atau password salah"));

        boolean valid = identityProviderClient.validateCredentials(request.username(), request.password());
        if (!valid) {
            throw new IllegalArgumentException("Username atau password salah");
        }

        user.setLastLoginAt(Instant.now());

        String token = tokenProvider.generateToken(user);
        log.info("Login berhasil untuk user: {}", user.getUsername());

        return new LoginResponse(
            token,
            user.getUsername(),
            user.getRole().getRoleName(),
            jwtProperties.tokenValidityInMinutes()
        );
    }

    /**
     * Flow register:
     * 1. Validasi uniqueness username dan email di database lokal
     * 2. Validasi role yang diminta ada di database
     * 3. Register ke Syncope (dapatkan syncopeRefId)
     * 4. Simpan User ke database lokal
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username '" + request.username() + "' sudah digunakan");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email '" + request.email() + "' sudah terdaftar");
        }

        Role role = roleRepository.findByRoleName(request.roleName())
            .orElseThrow(() -> new IllegalArgumentException(
                "Role '" + request.roleName() + "' tidak dikenali. Pilihan valid: STAFF, MANAGER, AUDITOR, DIREKSI"
            ));

        // Registrasi ke Syncope - jika gagal, exception akan otomatis
        // rollback seluruh transaction (user tidak akan tersimpan di DB lokal)
        String syncopeRefId = identityProviderClient.registerUser(
            request.username(), request.email(), request.password()
        );

        User newUser = new User();
        newUser.setUsername(request.username());
        newUser.setEmail(request.email());
        newUser.setSyncopeRefId(syncopeRefId);
        newUser.setRole(role);
        newUser.setIsActive(true);

        User saved = userRepository.save(newUser);
        log.info("User baru '{}' berhasil diregistrasi", saved.getUsername());

        return new RegisterResponse(
            saved.getUserId().toString(),
            saved.getUsername(),
            saved.getEmail(),
            saved.getRole().getRoleName()
        );
    }
}
