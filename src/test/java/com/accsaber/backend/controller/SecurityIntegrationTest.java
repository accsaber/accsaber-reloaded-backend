package com.accsaber.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.accsaber.backend.controller.ranking.RankingBatchController;
import com.accsaber.backend.controller.staff.StaffItemController;
import com.accsaber.backend.controller.staff.StaffUnusualEffectController;
import com.accsaber.backend.controller.staff.StaffUserController;

@ExtendWith(MockitoExtension.class)
class SecurityIntegrationTest {

        @Test
        void rankingBatchController_hasRankingHeadClassLevelPreAuthorize() {
                PreAuthorize annotation = RankingBatchController.class.getAnnotation(PreAuthorize.class);

                assertThat(annotation).isNotNull();
                assertThat(annotation.value()).isEqualTo("hasRole('RANKING_HEAD')");
        }

        @Test
        void staffUserController_hasAdminClassLevelPreAuthorize() {
                PreAuthorize annotation = StaffUserController.class.getAnnotation(PreAuthorize.class);

                assertThat(annotation).isNotNull();
                assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
        }

        @Test
        void staffItemController_hasAdminOrCreativeClassLevelPreAuthorize() {
                PreAuthorize annotation = StaffItemController.class.getAnnotation(PreAuthorize.class);

                assertThat(annotation).isNotNull();
                assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN', 'CREATIVE')");
        }

        @Test
        void staffUnusualEffectController_hasAdminOrCreativeClassLevelPreAuthorize() {
                PreAuthorize annotation = StaffUnusualEffectController.class.getAnnotation(PreAuthorize.class);

                assertThat(annotation).isNotNull();
                assertThat(annotation.value()).isEqualTo("hasAnyRole('ADMIN', 'CREATIVE')");
        }

        @Test
        void roleHierarchy_adminImpliesRankingHeadAndRanking() {
                RoleHierarchy hierarchy = RoleHierarchyImpl.withDefaultRolePrefix()
                                .role("ADMIN").implies("RANKING_HEAD")
                                .role("RANKING_HEAD").implies("RANKING")
                                .build();

                Collection<? extends GrantedAuthority> reachable = hierarchy.getReachableGrantedAuthorities(
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

                assertThat(reachable)
                                .extracting(GrantedAuthority::getAuthority)
                                .contains("ROLE_ADMIN", "ROLE_RANKING_HEAD", "ROLE_RANKING");
        }

        @Test
        void roleHierarchy_rankingHeadImpliesRanking() {
                RoleHierarchy hierarchy = RoleHierarchyImpl.withDefaultRolePrefix()
                                .role("ADMIN").implies("RANKING_HEAD")
                                .role("RANKING_HEAD").implies("RANKING")
                                .build();

                Collection<? extends GrantedAuthority> reachable = hierarchy.getReachableGrantedAuthorities(
                                List.of(new SimpleGrantedAuthority("ROLE_RANKING_HEAD")));

                assertThat(reachable)
                                .extracting(GrantedAuthority::getAuthority)
                                .contains("ROLE_RANKING_HEAD", "ROLE_RANKING")
                                .doesNotContain("ROLE_ADMIN");
        }

        @Test
        void roleHierarchy_rankingHasNoImpliedRoles() {
                RoleHierarchy hierarchy = RoleHierarchyImpl.withDefaultRolePrefix()
                                .role("ADMIN").implies("RANKING_HEAD")
                                .role("RANKING_HEAD").implies("RANKING")
                                .build();

                Collection<? extends GrantedAuthority> reachable = hierarchy.getReachableGrantedAuthorities(
                                List.of(new SimpleGrantedAuthority("ROLE_RANKING")));

                assertThat(reachable)
                                .extracting(GrantedAuthority::getAuthority)
                                .containsExactly("ROLE_RANKING");
        }
}
