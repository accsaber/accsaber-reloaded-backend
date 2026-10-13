package com.accsaber.backend.repository.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.market.MarketBid;
import com.accsaber.backend.model.entity.market.MarketListing;
import com.accsaber.backend.model.entity.market.MarketListingStatus;
import com.accsaber.backend.model.entity.user.User;

import jakarta.persistence.EntityManager;

@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MarketInvolvementQueryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private MarketListingRepository listingRepository;

    private User seller;
    private User outbid;
    private User leader;
    private UUID listingId;

    @BeforeEach
    void seed() {
        seller = user(1L, "Seller");
        outbid = user(2L, "Outbid");
        leader = user(3L, "Leader");
        ItemType type = entityManager.createQuery("SELECT t FROM ItemType t WHERE t.key = :key", ItemType.class)
                .setParameter("key", "profile_border").getSingleResult();
        Item item = Item.builder().type(type).name("Market Border").serialized(false).build();
        entityManager.persist(item);

        MarketListing listing = MarketListing.builder()
                .seller(seller)
                .item(item)
                .title("Market Border")
                .quantity(1)
                .startingBid(10L)
                .currentBid(20L)
                .currentBidder(leader)
                .endsAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
        entityManager.persist(listing);
        entityManager.persist(MarketBid.builder().listing(listing).bidder(outbid).amount(10L).build());
        entityManager.persist(MarketBid.builder().listing(listing).bidder(leader).amount(20L).build());
        entityManager.flush();
        entityManager.clear();
        listingId = listing.getId();
    }

    @Test
    @DisplayName("a listing you were outbid on still shows up in your activity")
    void anOutbidListingIsStillYours() {
        assertThat(involvedIds(outbid)).containsExactly(listingId);
        assertThat(involvedIds(leader)).containsExactly(listingId);
        assertThat(involvedIds(seller)).containsExactly(listingId);
        assertThat(involvedIds(user(4L, "Stranger"))).isEmpty();
    }

    private List<UUID> involvedIds(User user) {
        return listingRepository.findInvolvingUser(user.getId(), List.of(MarketListingStatus.active), Pageable.unpaged())
                .map(MarketListing::getId).getContent();
    }

    private User user(Long id, String name) {
        User user = User.builder().id(id).name(name).country("ES").build();
        entityManager.persist(user);
        return user;
    }
}
