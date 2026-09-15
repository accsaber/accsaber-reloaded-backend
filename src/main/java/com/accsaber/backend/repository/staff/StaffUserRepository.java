package com.accsaber.backend.repository.staff;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.model.entity.staff.StaffUserStatus;

public interface StaffUserRepository extends JpaRepository<StaffUser, UUID> {

    @Query("SELECT s FROM StaffUser s LEFT JOIN FETCH s.user WHERE s.id IN :ids")
    List<StaffUser> findAllByIdWithUser(@Param("ids") List<UUID> ids);

    List<StaffUser> findByUsernameAndActiveTrue(String username);

    List<StaffUser> findByUsernameIgnoreCaseAndActiveTrue(String username);

    Optional<StaffUser> findByUsernameAndRoleAndActiveTrue(String username, StaffRole role);

    Optional<StaffUser> findByUsernameIgnoreCaseAndRoleAndActiveTrue(String username, StaffRole role);

    Optional<StaffUser> findByEmailAndActiveTrue(String email);

    Optional<StaffUser> findByEmailIgnoreCaseAndActiveTrue(String email);

    Optional<StaffUser> findByRefreshToken(String refreshToken);

    Optional<StaffUser> findByIdAndActiveTrue(UUID id);

    List<StaffUser> findByUserIdAndRoleInAndStatusAndActiveTrue(
            Long userId, Collection<StaffRole> roles, StaffUserStatus status);

    List<StaffUser> findByUserIdAndStatusAndActiveTrue(Long userId, StaffUserStatus status);

    boolean existsByUserIdAndActiveTrue(Long userId);

    List<StaffUser> findAllByActiveTrue();

    Page<StaffUser> findAllByActiveTrue(Pageable pageable);

    Page<StaffUser> findAllByActiveFalse(Pageable pageable);

    Page<StaffUser> findAllByActiveTrueAndStatus(StaffUserStatus status, Pageable pageable);

    Optional<StaffUser> findByIdAndActiveTrueAndStatus(UUID id, StaffUserStatus status);

    Page<StaffUser> findAllByStatus(StaffUserStatus status, Pageable pageable);

    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM news WHERE staff_user_id = :id)
                OR EXISTS (SELECT 1 FROM admin_actions WHERE staff_user_id = :id)
            """, nativeQuery = true)
    boolean hasAuthoredRecords(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            WITH dropped_votes AS (
                DELETE FROM staff_map_votes WHERE staff_id = :id
            ), batch_creators AS (
                UPDATE batches SET created_by = NULL WHERE created_by = :id
            ), difficulty_editors AS (
                UPDATE map_difficulties
                SET created_by = NULLIF(created_by, :id), last_updated_by = NULLIF(last_updated_by, :id)
                WHERE created_by = :id OR last_updated_by = :id
            ), alias_creators AS (
                UPDATE map_difficulty_leaderboard_aliases SET created_by = NULL WHERE created_by = :id
            ), campaign_distinctions AS (
                UPDATE campaigns
                SET curated_by = NULLIF(curated_by, :id), loved_by = NULLIF(loved_by, :id)
                WHERE curated_by = :id OR loved_by = :id
            ), merge_actors AS (
                UPDATE users_duplicate_links SET merged_by = NULL WHERE merged_by = :id
            )
            UPDATE user_item_links SET awarded_by = NULL WHERE awarded_by = :id
            """, nativeQuery = true)
    void detachStaffReferences(@Param("id") UUID id);
}
