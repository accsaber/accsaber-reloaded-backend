package com.accsaber.backend.repository.item;

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

import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.entity.user.User;

import jakarta.persistence.EntityManager;

@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InventoryUnownedQueryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    private static final String SEARCH = "Probe";

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private UserItemLinkRepository linkRepository;

    private User owner;

    @BeforeEach
    void seed() {
        owner = User.builder().id(1L).name("Owner").country("ES").build();
        entityManager.persist(owner);
        Item owned = item("Probe Owned", true);
        item("Probe Missing", true);
        item("Probe Hidden", false);
        entityManager.persist(UserItemLink.builder().user(owner).item(owned).quantity(2L).source(ItemSource.event)
                .build());
        entityManager.persist(UserItemLink.builder().user(owner).item(owned).quantity(1L).source(ItemSource.event)
                .build());
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("owned only lists each owned link")
    void ownedOnlyListsLinks() {
        assertThat(names(false)).containsExactlyInAnyOrder("Probe Owned:link", "Probe Owned:link");
    }

    @Test
    @DisplayName("includeUnowned adds one linkless row per visible item the player lacks")
    void includeUnownedAddsVisibleMissingItems() {
        assertThat(names(true))
                .containsExactlyInAnyOrder("Probe Owned:link", "Probe Owned:link", "Probe Missing:none");
    }

    private List<String> names(boolean includeUnowned) {
        return linkRepository.findInventoryFiltered(owner.getId(), includeUnowned, null, null, null, null, null,
                null, false, SEARCH, Pageable.unpaged())
                .map(row -> ((Item) row[0]).getName() + (row[1] != null ? ":link" : ":none"))
                .getContent();
    }

    private Item item(String name, boolean visible) {
        ItemType type = entityManager.createQuery("SELECT t FROM ItemType t WHERE t.key = :key", ItemType.class)
                .setParameter("key", "profile_border").getSingleResult();
        Item item = Item.builder().type(type).name(name).visible(visible).serialized(false).build();
        entityManager.persist(item);
        return item;
    }
}
