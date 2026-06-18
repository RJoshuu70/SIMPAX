package id.ac.upnvj.simpax.repository;

import id.ac.upnvj.simpax.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    /**
     * Dipakai pada proses login: mencari user berdasarkan username sebelum
     * memvalidasi password ke Syncope.
     *
     * <p>Anotasi @EntityGraph di sini SENGAJA ditambahkan untuk meng-override
     * FetchType.LAZY yang sudah didefinisikan pada relasi User.role. Pada
     * proses login, kita SELALU butuh data Role (untuk dimasukkan sebagai
     * claim JWT), sehingga lebih efisien mengambilnya sekaligus dalam satu
     * query (JOIN FETCH) daripada membiarkan Hibernate melakukan query
     * tambahan terpisah saat role diakses nanti (yang disebut N+1 query
     * problem - salah satu penyebab paling umum aplikasi Spring Boot
     * menjadi lambat tanpa disadari developernya).
     */
    @EntityGraph(attributePaths = "role")
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findBySyncopeRefId(String syncopeRefId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
