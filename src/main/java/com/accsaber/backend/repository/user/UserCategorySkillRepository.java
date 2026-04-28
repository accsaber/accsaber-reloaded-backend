package com.accsaber.backend.repository.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.user.UserCategorySkill;
import com.accsaber.backend.model.entity.user.UserCategorySkillId;

public interface UserCategorySkillRepository extends JpaRepository<UserCategorySkill, UserCategorySkillId> {

        @Query("""
                        SELECT s FROM UserCategorySkill s
                        JOIN FETCH s.category c
                        WHERE s.user.id = :userId AND c.active = true
                        """)
        List<UserCategorySkill> findByUserIdActive(@Param("userId") Long userId);

        @Query("""
                        SELECT s FROM UserCategorySkill s
                        JOIN FETCH s.category c
                        WHERE s.user.id = :userId AND c.id = :categoryId
                        """)
        Optional<UserCategorySkill> findByUserIdAndCategoryId(
                        @Param("userId") Long userId,
                        @Param("categoryId") UUID categoryId);

        @Query("""
                        SELECT s FROM UserCategorySkill s
                        JOIN FETCH s.category c
                        WHERE s.user.id = :userId AND c.countForOverall = true AND c.active = true
                        """)
        List<UserCategorySkill> findByUserIdForOverall(@Param("userId") Long userId);
}
