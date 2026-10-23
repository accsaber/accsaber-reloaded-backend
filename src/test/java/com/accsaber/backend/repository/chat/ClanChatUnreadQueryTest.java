package com.accsaber.backend.repository.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.accsaber.backend.model.entity.chat.ChatEvent;
import com.accsaber.backend.model.entity.chat.ChatMessage;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.user.User;

import jakarta.persistence.EntityManager;

@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ClanChatUnreadQueryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ChatMessageRepository chatRepository;

    private UUID clanId;
    private Instant before;

    @BeforeEach
    void seed() {
        before = Instant.now().minus(1, ChronoUnit.MINUTES);
        User reader = user(1L);
        User other = user(2L);
        Clan clan = Clan.builder().name("Owls").tag("OWL").slug("owls").build();
        entityManager.persist(clan);
        clanId = clan.getId();
        message(clan, reader, "mine");
        message(clan, other, "hi");
        message(clan, other, "there");
        entityManager.persist(ChatMessage.builder().clan(clan).event(ChatEvent.member_joined).build());
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("counts messages after since that the reader did not write up to the cap, events kept or split out")
    void countsOthersMessagesUpToTheCap() {
        assertThat(chatRepository.countClanUnread(clanId, 1L, before, null, 99)).isEqualTo(3);
        assertThat(chatRepository.countClanUnread(clanId, 1L, before, false, 99)).isEqualTo(2);
        assertThat(chatRepository.countClanUnread(clanId, 1L, before, true, 99)).isEqualTo(1);
        assertThat(chatRepository.countClanUnread(clanId, 1L, before, null, 2)).isEqualTo(2);
        assertThat(chatRepository.countClanUnread(clanId, 1L, Instant.now().plus(1, ChronoUnit.MINUTES), null, 99))
                .isZero();
    }

    @Test
    @DisplayName("latest message time is the newest row, or null for an empty clan")
    void latestMessageTime() {
        assertThat(chatRepository.findLatestClanMessageAt(clanId, null)).isAfter(before);
        assertThat(chatRepository.findLatestClanMessageAt(clanId, false)).isAfter(before);
        assertThat(chatRepository.findLatestClanMessageAt(UUID.randomUUID(), null)).isNull();
    }

    private User user(long id) {
        User user = User.builder().id(id).name("Player " + id).country("ES").build();
        entityManager.persist(user);
        return user;
    }

    private void message(Clan clan, User author, String content) {
        entityManager.persist(ChatMessage.builder().clan(clan).user(author).content(content).build());
    }
}
