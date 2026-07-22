package com.accsaber.backend.service.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.accsaber.backend.model.dto.response.item.UnusualEffectGroupsResponse;
import com.accsaber.backend.model.entity.item.CrateUnusualEffect;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.model.entity.item.UnusualEffect;
import com.accsaber.backend.repository.item.CrateUnusualEffectRepository;
import com.accsaber.backend.repository.item.UnusualEffectRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UnusualEffectServiceGroupedTest {

    @Mock
    private UnusualEffectRepository unusualEffectRepository;
    @Mock
    private CrateUnusualEffectRepository crateUnusualEffectRepository;

    @InjectMocks
    private UnusualEffectService service;

    @Test
    void effectsAreGroupedUnderTheirCrate() {
        Item alpha = crate("Alpha Crate", true);
        UnusualEffect fiery = effect("fiery", "Fiery");
        UnusualEffect angelic = effect("angelic", "Angelic");
        stub(List.of(attach(alpha, fiery), attach(alpha, angelic)), List.of(fiery, angelic));

        UnusualEffectGroupsResponse res = service.findAllGrouped(false);

        assertThat(res.getGroups()).hasSize(1);
        assertThat(res.getGroups().get(0).getCrateName()).isEqualTo("Alpha Crate");
        assertThat(res.getGroups().get(0).getEffects()).extracting("key")
                .containsExactly("fiery", "angelic");
        assertThat(res.getUngrouped()).isEmpty();
    }

    @Test
    void anEffectInTwoCratesAppearsUnderBoth() {
        Item alpha = crate("Alpha Crate", true);
        Item beta = crate("Beta Crate", true);
        UnusualEffect fiery = effect("fiery", "Fiery");
        stub(List.of(attach(alpha, fiery), attach(beta, fiery)), List.of(fiery));

        UnusualEffectGroupsResponse res = service.findAllGrouped(false);

        assertThat(res.getGroups()).hasSize(2);
        assertThat(res.getGroups()).allSatisfy(g -> assertThat(g.getEffects()).hasSize(1));
        assertThat(res.getUngrouped()).isEmpty();
    }

    @Test
    void anEffectAttachedToNoCrateLandsInUngrouped() {
        UnusualEffect orphan = effect("orphan", "Orphan");
        stub(List.of(), List.of(orphan));

        UnusualEffectGroupsResponse res = service.findAllGrouped(false);

        assertThat(res.getGroups()).isEmpty();
        assertThat(res.getUngrouped()).extracting("key").containsExactly("orphan");
    }

    @Test
    void anEffectThatOnlyDropsFromAHiddenCrateIsOmittedEntirely() {
        Item unreleased = crate("Alpha Crate", false);
        UnusualEffect secret = effect("secret", "Secret");
        stub(List.of(attach(unreleased, secret)), List.of(secret));

        UnusualEffectGroupsResponse res = service.findAllGrouped(false);

        assertThat(res.getGroups()).isEmpty();
        assertThat(res.getUngrouped())
                .as("a hidden crate's exclusive effect must not leak into ungrouped")
                .isEmpty();
    }

    @Test
    void aHiddenCrateEffectStillShowsUnderAVisibleCrateItAlsoDropsFrom() {
        Item unreleased = crate("Alpha Crate", false);
        Item released = crate("Beta Crate", true);
        UnusualEffect shared = effect("shared", "Shared");
        stub(List.of(attach(unreleased, shared), attach(released, shared)), List.of(shared));

        UnusualEffectGroupsResponse res = service.findAllGrouped(false);

        assertThat(res.getGroups()).hasSize(1);
        assertThat(res.getGroups().get(0).getCrateName()).isEqualTo("Beta Crate");
        assertThat(res.getUngrouped()).isEmpty();
    }

    @Test
    void adminViewIncludesHiddenCrates() {
        Item unreleased = crate("Alpha Crate", false);
        UnusualEffect secret = effect("secret", "Secret");
        stub(List.of(attach(unreleased, secret)), List.of(secret));

        UnusualEffectGroupsResponse res = service.findAllGrouped(true);

        assertThat(res.getGroups()).hasSize(1);
        assertThat(res.getGroups().get(0).getCrateName()).isEqualTo("Alpha Crate");
    }

    private void stub(List<CrateUnusualEffect> attachments, List<UnusualEffect> activeEffects) {
        when(crateUnusualEffectRepository.findAllHydrated()).thenReturn(attachments);
        when(unusualEffectRepository.findByActiveTrue()).thenReturn(activeEffects);
    }

    private static CrateUnusualEffect attach(Item crate, UnusualEffect effect) {
        return CrateUnusualEffect.builder().crateItem(crate).effect(effect).build();
    }

    private static Item crate(String name, boolean visible) {
        return Item.builder()
                .id(UUID.randomUUID())
                .type(ItemType.builder().key("crate").name("Crate").build())
                .name(name)
                .visible(visible)
                .active(true)
                .build();
    }

    private static UnusualEffect effect(String key, String name) {
        return UnusualEffect.builder()
                .id(UUID.randomUUID())
                .key(key)
                .name(name)
                .active(true)
                .build();
    }
}
