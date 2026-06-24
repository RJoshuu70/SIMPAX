package id.ac.upnvj.simpax.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.AbstractContextMapper;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * FortressRbacService - pengganti fortress-core untuk PoC akademik.
 *
 * <p>Menggunakan Spring LDAP untuk query role assignment dari OpenLDAP,
 * menggantikan fortress-core yang tidak kompatibel dengan Spring Boot 3.x.
 *
 * <p>RBAC Matrix (sesuai PRD):
 * - STAFF    : CREATE transaksi, READ transaksi
 * - MANAGER  : CREATE + APPROVE transaksi, READ dashboard
 * - AUDITOR  : READ semua (read-only, tanpa CREATE/APPROVE)
 * - DIREKSI  : READ dashboard + laporan laba rugi
 */
@Service
public class FortressRbacService {

    private static final Logger log = LoggerFactory.getLogger(FortressRbacService.class);

    private final LdapTemplate ldapTemplate;

    public FortressRbacService(LdapTemplate ldapTemplate) {
        this.ldapTemplate = ldapTemplate;
    }

    /**
     * Cek apakah role tertentu terdaftar di OpenLDAP.
     */
    public boolean roleExists(String roleName) {
        try {
            List<String> results = ldapTemplate.search(
                LdapQueryBuilder.query()
                    .base("ou=Roles")
                    .where("objectclass").is("groupOfNames")
                    .and("cn").is(roleName),
                new AbstractContextMapper<String>() {
                    @Override
                    protected String doMapFromContext(DirContextOperations ctx) {
                        return ctx.getDn().toString();
                    }
                }
            );
            boolean exists = !results.isEmpty();
            log.debug("Role '{}' di OpenLDAP: {}", roleName, exists ? "ditemukan" : "tidak ditemukan");
            return exists;
        } catch (Exception e) {
            log.error("Gagal query OpenLDAP untuk role {}: {}", roleName, e.getMessage());
            return false;
        }
    }

    /**
     * RBAC access check - apakah role boleh melakukan operasi pada resource.
     *
     * @param role       nama role (STAFF/MANAGER/AUDITOR/DIREKSI)
     * @param operation  operasi yang diminta (CREATE/READ/APPROVE)
     * @param resource   nama resource (TRANSACTION/DASHBOARD/REPORT)
     * @return true jika diizinkan
     */
    public boolean checkAccess(String role, String operation, String resource) {
        if (!roleExists(role)) {
            log.warn("Role '{}' tidak ditemukan di OpenLDAP, akses ditolak", role);
            return false;
        }

        boolean allowed = switch (role.toUpperCase()) {
            case "STAFF" -> switch (operation.toUpperCase()) {
                case "CREATE", "READ" -> "TRANSACTION".equals(resource.toUpperCase());
                default -> false;
            };
            case "MANAGER" -> switch (operation.toUpperCase()) {
                case "CREATE", "READ", "APPROVE" -> true;
                default -> false;
            };
            case "AUDITOR" -> "READ".equals(operation.toUpperCase());
            case "DIREKSI" -> "READ".equals(operation.toUpperCase());
            default -> false;
        };

        log.info("RBAC check: role={} operation={} resource={} -> {}",
            role, operation, resource, allowed ? "ALLOWED" : "DENIED");
        return allowed;
    }
}