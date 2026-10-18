package com.logistics.proyect.group5.repository;

import com.logistics.proyect.group5.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Asigna el rol por defecto a un usuario <b>solo si no tiene ningún rol</b>.
     * <p>
     * Es idempotente y seguro en cualquier momento del ciclo de vida: nunca añade
     * un rol secundario a una cuenta que ya tiene uno (p. ej. un ADMIN no recibe
     * CLIENTE). Acepta el nombre con o sin el prefijo {@code ROLE_} para tolerar
     * bases que aún no han pasado por la migración de nombres.
     * <p>
     * Requiere una transacción abierta y que el usuario ya exista en la tabla
     * {@code auth_schema.users} (hay FK desde {@code user_roles}).
     *
     * @return número de filas insertadas (0 si el usuario ya tenía algún rol)
     */
    @Modifying
    @Query(value = """
            INSERT INTO auth_schema.user_roles (user_id, role_id)
            SELECT :userId, r.id
              FROM auth_schema.roles r
             WHERE r.name IN (:roleName, CONCAT('ROLE_', :roleName))
               AND NOT EXISTS (
                   SELECT 1 FROM auth_schema.user_roles ur
                    WHERE ur.user_id = :userId)
            """, nativeQuery = true)
    int assignDefaultRoleIfNone(@Param("userId") UUID userId, @Param("roleName") String roleName);
}
