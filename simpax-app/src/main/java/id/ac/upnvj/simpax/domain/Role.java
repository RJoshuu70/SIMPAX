package id.ac.upnvj.simpax.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity Role merepresentasikan tabel "roles" pada PRD Bab 10.2.
 *
 * <p>Tabel ini menjadi referensi hierarki otorisasi yang secara konseptual
 * dikelola oleh Apache Fortress (RBAC Engine - domain Zaskia). Backend ini
 * (Spring Boot) menyimpan salinan lokal dari definisi role agar query
 * relasional (JOIN ke tabel users) tetap efisien tanpa harus memanggil
 * Fortress setiap kali butuh nama role.
 *
 * <p>Catatan desain: field hierarchyLevel sengaja bertipe Integer (bukan enum)
 * agar fleksibel ditambah role baru tanpa perlu redeploy aplikasi -
 * cukup INSERT baris baru ke tabel roles.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Long roleId;

    @NotNull
    @Size(max = 50)
    @Column(name = "role_name", length = 50, nullable = false, unique = true)
    private String roleName;

    @NotNull
    @Column(name = "hierarchy_level", nullable = false)
    private Integer hierarchyLevel;

    @Column(name = "description")
    private String description;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Role)) {
            return false;
        }
        return roleId != null && roleId.equals(((Role) o).roleId);
    }

    @Override
    public int hashCode() {
        // Menggunakan nilai konstan untuk entity yang belum persisted (id null),
        // sesuai rekomendasi resmi Hibernate untuk entity equality berbasis primary key.
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Role{" + "roleId=" + roleId + ", roleName='" + roleName + "'" + ", hierarchyLevel=" + hierarchyLevel + "}";
    }
}
