package com.accsaber.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.accsaber.backend.controller.map.BatchController;
import com.accsaber.backend.controller.staff.StaffUserController;
import com.accsaber.backend.service.map.BatchService;

@ExtendWith(MockitoExtension.class)
class SecurityIntegrationTest {

        @Mock
        private BatchService batchService;

        @InjectMocks
        private BatchController batchController;

        @Test
        void releaseEndpoint_hasRankingHeadPreAuthorize() throws NoSuchMethodException {
                Method release = BatchController.class.getMethod("release", UUID.class);
                PreAuthorize annotation = release.getAnnotation(PreAuthorize.class);

                assertThat(annotation).isNotNull();
                assertThat(annotation.value()).isEqualTo("hasRole('RANKING_HEAD')");
        }

        @Test
        void createBatchEndpoint_hasRankingHeadPreAuthorize() throws NoSuchMethodException {
                Method create = BatchController.class.getMethod("createBatch",
                                com.accsaber.backend.model.dto.request.map.CreateBatchRequest.class,
                                com.accsaber.backend.security.StaffUserDetails.class);
                PreAuthorize annotation = create.getAnnotation(PreAuthorize.class);

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
