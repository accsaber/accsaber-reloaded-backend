package com.accsaber.backend.repository.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

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
import com.accsaber.backend.model.entity.item.ItemModifier;
import com.accsaber.backend.model.entity.item.ItemSource;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.entity.user.User;

import jakarta.persistence.EntityManager;

@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InventoryFilteredQueryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    private static final String SEARCH = "Probe";

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private UserItemLinkRepository linkRepository;

    private User owner;
    private ItemModifier modifier;
    private UUID singleLinkId;

    @BeforeEach
    void seed() {
        owner = User.builder().id(1L).name("Owner").country("ES").build();
        entityManager.persist(owner);
        Item owned = item("Probe Owned", true);
        item("Probe Missing", true);
        item("Probe Hidden", false);
        item("Probe Clan", true, "clan_border");
        modifier = entityManager.createQuery("SELECT m FROM ItemModifier m", ItemModifier.class).setMaxResults(1)
                .getSingleResult();
        UserItemLink modified = link(owned, 1L);
        modified.setModifiers(new HashSet<>(List.of(modifier)));
        link(owned, 1L);
        link(item("Probe Stack", true), 2L);
        singleLinkId = link(item("Probe Single", true), 1L).getId();
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("owned only lists each owned link")
    void ownedOnlyListsLinks() {
        assertThat(names(false, null, null)).containsExactlyInAnyOrder("Probe Owned:link", "Probe Owned:link",
                "Probe Stack:link", "Probe Single:link");
    }

    @Test
    @DisplayName("includeUnowned adds one linkless row per visible item the player lacks")
    void includeUnownedAddsVisibleMissingItems() {
        assertThat(names(true, null, null)).containsExactlyInAnyOrder("Probe Owned:link", "Probe Owned:link",
                "Probe Stack:link", "Probe Single:link", "Probe Missing:none");
    }

    @Test
    @DisplayName("duplicates keeps every link of an item held more than once, stacks included")
    void duplicatesKeepsItemsHeldMoreThanOnce() {
        assertThat(names(false, null, true)).containsExactlyInAnyOrder("Probe Owned:link", "Probe Owned:link",
                "Probe Stack:link");
    }

    @Test
    @DisplayName("duplicates counts only the links the other filters keep")
    void duplicatesCountsOnlyFilteredLinks() {
        assertThat(names(false, List.of(modifier.getKey()), null)).containsExactly("Probe Owned:link");
        assertThat(names(false, List.of(modifier.getKey()), true)).isEmpty();
    }

    @Test
    @DisplayName("duplicates=false keeps the items held once")
    void duplicatesFalseKeepsSingles() {
        assertThat(names(false, null, false)).containsExactly("Probe Single:link");
    }

    @Test
    @DisplayName("equipped keeps the equipped links, and equipped=false keeps the rest")
    void equippedSplitsTheInventory() {
        assertThat(names(false, null, null, true)).containsExactly("Probe Single:link");
        assertThat(names(false, null, null, false)).containsExactlyInAnyOrder("Probe Owned:link",
                "Probe Owned:link", "Probe Stack:link");
    }

    private List<String> names(boolean includeUnowned, List<String> modifierKeys, Boolean duplicates) {
        return names(includeUnowned, modifierKeys, duplicates, null);
    }

    private List<String> names(boolean includeUnowned, List<String> modifierKeys, Boolean duplicates,
            Boolean equipped) {
        return linkRepository.findInventoryFiltered(owner.getId(), includeUnowned, null, null, modifierKeys, null,
                null, null, false, SEARCH, duplicates, equipped, equipped == null ? null : List.of(singleLinkId),
                Pageable.unpaged())
                .map(row -> ((Item) row[0]).getName() + (row[1] != null ? ":link" : ":none"))
                .getContent();
    }

    private UserItemLink link(Item item, long quantity) {
        UserItemLink link = UserItemLink.builder().user(owner).item(item).quantity(quantity)
                .source(ItemSource.event).build();
        entityManager.persist(link);
        return link;
    }

    private Item item(String name, boolean visible) {
        return item(name, visible, "profile_border");
    }

    private Item item(String name, boolean visible, String typeKey) {
        ItemType type = entityManager.createQuery("SELECT t FROM ItemType t WHERE t.key = :key", ItemType.class)
                .setParameter("key", typeKey).getSingleResult();
        Item item = Item.builder().type(type).name(name).visible(visible).serialized(false).build();
        entityManager.persist(item);
        return item;
    }
}
