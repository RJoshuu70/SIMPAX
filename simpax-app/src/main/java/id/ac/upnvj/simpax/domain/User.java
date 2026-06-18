package id.ac.upnvj.simpax.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity User sesuai PRD Bab 10.3.
 *
 * <p>Tabel ini berfungsi sebagai referensi lokal terhadap identitas yang
 * sumber kebenarannya (source of truth) berada pada Apache Syncope (domain
 * Zaskia/IAM). Field syncopeRefId menjadi jembatan korelasi antara baris
 * pada tabel ini dengan record identity asli di Syncope.
 *
 * <p>PENTING - terkait keamanan: entity ini SENGAJA tidak menyimpan kolom
 * password di sini. Otentikasi kredensial (password hashing, dsb) menjadi
 * tanggung jawab Apache Syncope sebagai Identity Provider. Backend ini hanya
 * menerbitkan JWT setelah proses login berhasil divalidasi - lihat
 * security.jwt.TokenProvider untuk detail penerbitan token.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    private UUID userId;

    @NotNull
    @Size(max = 100)
    @Column(name = "username", length = 100, nullable = false, unique = true)
    private String username;

    @NotNull
    @Email
    @Size(max = 150)
    @Column(name = "email", length = 150, nullable = false, unique = true)
    private String email;

    @NotNull
    @Size(max = 100)
    @Column(name = "syncope_ref_id", length = 100, nullable = false, unique = true)
    private String syncopeRefId;

    /**
     * Relasi ManyToOne ke Role: banyak User bisa memiliki satu Role yang sama.
     * FetchType.LAZY dipilih secara sengaja (bukan default JPA untuk ManyToOne
     * yang sebenarnya EAGER) agar query mengambil User TIDAK otomatis ikut
     * menarik data Role kecuali benar-benar diakses - penting untuk performa
     * saat memuat daftar User dalam jumlah besar pada dashboard.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @NotNull
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User)) {
            return false;
        }
        return userId != null && userId.equals(((User) o).userId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "User{" + "userId=" + userId + ", username='" + username + "'" + ", isActive=" + isActive + "}";
    }
}
