package com.accsaber.backend.repository.news;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accsaber.backend.model.entity.news.News;
import com.accsaber.backend.model.entity.news.NewsStatus;

public interface NewsRepository extends JpaRepository<News, UUID> {

    @EntityGraph(attributePaths = {"staffUser", "staffUser.user", "batch", "campaign", "milestoneSet", "curve"})
    Optional<News> findByIdAndActiveTrue(UUID id);

    @EntityGraph(attributePaths = {"staffUser", "staffUser.user", "batch", "campaign", "milestoneSet", "curve"})
    Optional<News> findBySlugAndActiveTrue(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    @EntityGraph(attributePaths = {"staffUser", "staffUser.user"})
    @Query("""
            SELECT n FROM News n
            WHERE n.active = true
              AND (:status IS NULL OR n.status = :status)
              AND (:authorId IS NULL OR n.staffUser.id = :authorId)
              AND (CAST(:search AS string) IS NULL
                   OR search_normalize(n.title) LIKE CONCAT('%', search_normalize(CAST(:search AS string)), '%')
                   OR search_normalize(n.description) LIKE CONCAT('%', search_normalize(CAST(:search AS string)), '%'))
              AND (
                :typeFilter = 0
                OR (:typeFilter = 1 AND n.batch IS NOT NULL)
                OR (:typeFilter = 2 AND n.campaign IS NOT NULL)
                OR (:typeFilter = 3 AND n.milestoneSet IS NOT NULL)
                OR (:typeFilter = 4 AND n.curve IS NOT NULL)
                OR (:typeFilter = 5
                    AND n.batch IS NULL
                    AND n.campaign IS NULL
                    AND n.milestoneSet IS NULL
                    AND n.curve IS NULL)
              )
            """)
    Page<News> search(
            @Param("status") NewsStatus status,
            @Param("authorId") UUID authorId,
            @Param("typeFilter") int typeFilter,
            @Param("search") String search,
            Pageable pageable);
}
