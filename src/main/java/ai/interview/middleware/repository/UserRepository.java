package ai.interview.middleware.repository;

import ai.interview.middleware.domain.entity.User;
import ai.interview.middleware.domain.enums.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByIdAndEnabledTrue(UUID id);

    List<User> findAllByRoleAndEnabledTrueOrderByFullNameAsc(Role role);

    /**
     * Written as a bulk update so a login does not dirty-check the whole entity and does not
     * contend with a concurrent profile update on the optimistic-lock version column.
     */
    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :timestamp WHERE u.id = :id")
    void touchLastLogin(@Param("id") UUID id, @Param("timestamp") java.time.Instant timestamp);
}
