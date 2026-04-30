package com.accsaber.backend.repository.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.accsaber.backend.model.entity.user.UserSetting;

@Repository
public interface UserSettingRepository extends JpaRepository<UserSetting, UUID> {

    Optional<UserSetting> findByUser_IdAndKey(Long userId, String key);

    List<UserSetting> findByUser_Id(Long userId);

    @Query("SELECT s FROM UserSetting s WHERE s.user.id = :userId AND s.key LIKE CONCAT(:prefix, '%')")
    List<UserSetting> findByUser_IdAndKeyPrefix(@Param("userId") Long userId, @Param("prefix") String prefix);
}
