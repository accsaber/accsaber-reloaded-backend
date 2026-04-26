package com.accsaber.backend.repository.news;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.accsaber.backend.model.entity.news.News;
import com.accsaber.backend.model.entity.news.NewsStatus;

public interface NewsRepository extends JpaRepository<News, UUID> {

    @EntityGraph(attributePaths = {"staffUser"})
    Page<News> findByActiveTrueAndStatus(NewsStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"staffUser", "batch", "campaign", "milestoneSet", "curve"})
    Optional<News> findByIdAndActiveTrue(UUID id);

    @EntityGraph(attributePaths = {"staffUser", "batch", "campaign", "milestoneSet", "curve"})
    Optional<News> findBySlugAndActiveTrue(String slug);

    @EntityGraph(attributePaths = {"staffUser"})
    Page<News> findByActiveTrue(Pageable pageable);

    @EntityGraph(attributePaths = {"staffUser"})
    Page<News> findByStaffUser_Id(UUID staffUserId, Pageable pageable);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);
}
