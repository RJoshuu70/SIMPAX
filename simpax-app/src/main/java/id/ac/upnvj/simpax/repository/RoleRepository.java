package id.ac.upnvj.simpax.repository;

import id.ac.upnvj.simpax.domain.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA secara otomatis mengimplementasikan interface ini saat
 * runtime - kita tidak perlu menulis kelas implementasi sendiri. Method
 * seperti save(), findById(), findAll(), deleteById() sudah otomatis
 * tersedia karena extends JpaRepository.
 *
 * <p>Method findByRoleName di bawah ini adalah contoh "derived query method"
 * - Spring Data JPA membaca nama method, mem-parsing-nya sebagai
 * "WHERE roleName = ?1", dan men-generate query SQL yang sesuai secara
 * otomatis TANPA kita perlu menulis JPQL/SQL manual.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByRoleName(String roleName);
}
