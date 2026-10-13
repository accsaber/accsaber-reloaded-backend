package com.accsaber.backend.repository.news;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Pageable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.accsaber.backend.model.entity.news.News;
import com.accsaber.backend.model.entity.news.NewsStatus;
import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUser;

import jakarta.persistence.EntityManager;

@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NewsQueryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private NewsRepository newsRepository;

    private News clans;
    private News curve;

    @BeforeEach
    void setUp() {
        StaffUser author = StaffUser.builder().username("writer").password("hashed").role(StaffRole.ADMIN).build();
        entityManager.persist(author);
        clans = persistNews(author, "Clans now LIVE!", "Fight wars and establish diplomacy");
        curve = persistNews(author, "Curve update", "Tech Acc weighting changes");
        entityManager.flush();
    }

    @Test
    @DisplayName("search matches title or description, ignoring case and accents")
    void searchMatchesTitleOrDescription() {
        assertThat(search("clans")).containsExactly(clans);
        assertThat(search("DIPLOMACY")).containsExactly(clans);
        assertThat(search("wéighting")).containsExactly(curve);
        assertThat(search("nothing like this")).isEmpty();
        assertThat(search(null)).containsExactlyInAnyOrder(clans, curve);
    }

    private List<News> search(String term) {
        return newsRepository.search(NewsStatus.PUBLISHED, null, 0, term, Pageable.unpaged()).getContent();
    }

    private News persistNews(StaffUser author, String title, String description) {
        News news = News.builder()
                .staffUser(author)
                .title(title)
                .slug(title.toLowerCase().replaceAll("[^a-z0-9]+", "-"))
                .description(description)
                .content("body")
                .status(NewsStatus.PUBLISHED)
                .build();
        entityManager.persist(news);
        return news;
    }
}
