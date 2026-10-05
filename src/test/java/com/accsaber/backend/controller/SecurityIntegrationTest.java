package com.accsaber.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RestController;

import com.accsaber.backend.config.SecurityConfig;
import com.accsaber.backend.controller.admin.AdminCrateController;
import com.accsaber.backend.controller.admin.AdminItemController;
import com.accsaber.backend.controller.admin.AdminNewsController;
import com.accsaber.backend.controller.admin.AdminUnusualEffectController;
import com.accsaber.backend.controller.ranking.RankingBatchController;
import com.accsaber.backend.controller.staff.StaffUserController;

@ExtendWith(MockitoExtension.class)
class SecurityIntegrationTest {

        private static final String ADMIN_CONTROLLER_PACKAGE = "com.accsaber.backend.controller.admin";

        private static final Pattern ROLE_LITERAL = Pattern.compile("'([A-Z_]+)'");

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
        void adminItemController_listItemsReadableByCreative() {
                assertThat(methodAuthorization(AdminItemController.class, "listItems"))
                                .isEqualTo("hasAnyRole('ADMIN', 'CREATIVE')");
        }

        @Test
        void adminUnusualEffectController_listReadableByCreative() {
                assertThat(methodAuthorization(AdminUnusualEffectController.class, "list"))
                                .isEqualTo("hasAnyRole('ADMIN', 'CREATIVE')");
        }

        @Test
        void adminCrateController_readsAreReadableByCreative() {
                assertThat(List.of("listCrates", "listContents"))
                                .allSatisfy(method -> assertThat(
                                                methodAuthorization(AdminCrateController.class, method))
                                                .isEqualTo("hasAnyRole('ADMIN', 'CREATIVE')"));
        }

        @Test
        void adminCrateController_writesStayAdminOnly() {
                assertThat(List.of("upsertContent", "removeContent", "upsertModifier", "removeModifier",
                                "attachUnusualEffect", "detachUnusualEffect"))
                                .allSatisfy(method -> assertThat(
                                                methodAuthorization(AdminCrateController.class, method))
                                                .isNull());

                assertThat(AdminCrateController.class.getAnnotation(PreAuthorize.class).value())
                                .isEqualTo("hasRole('ADMIN')");
        }

        @Test
        void adminNewsController_authoringIsOpenToRankingHeads() {
                assertThat(List.of("getById", "create", "update", "uploadImage", "deleteImage"))
                                .allSatisfy(method -> assertThat(
                                                methodAuthorization(AdminNewsController.class, method))
                                                .isEqualTo("hasAnyRole('ADMIN', 'RANKING_HEAD')"));

                assertThat(methodAuthorization(AdminNewsController.class, "list"))
                                .isEqualTo("#mine ? hasAnyRole('ADMIN', 'RANKING_HEAD') : hasRole('ADMIN')");
        }

        @Test
        void adminNewsController_deletionStaysAdminOnly() {
                assertThat(methodAuthorization(AdminNewsController.class, "delete")).isNull();

                assertThat(AdminNewsController.class.getAnnotation(PreAuthorize.class).value())
                                .isEqualTo("hasRole('ADMIN')");
        }

        @Test
        void everyRoleAdminControllersAuthorizeIsAdmittedByTheAdminPathGate() {
                Set<String> gate = Set.of(SecurityConfig.ADMIN_PATH_ROLES);

                assertThat(rolesReferencedByAdminControllers()).isNotEmpty().isSubsetOf(gate);
        }

        @Test
        void everyAdminControllerCarriesAClassLevelPreAuthorize() {
                assertThat(adminControllers())
                                .isNotEmpty()
                                .allSatisfy(controller -> assertThat(controller.getAnnotation(PreAuthorize.class))
                                                .as(controller.getSimpleName())
                                                .isNotNull());
        }

        private Set<String> rolesReferencedByAdminControllers() {
                Set<String> roles = new TreeSet<>();
                for (Class<?> controller : adminControllers()) {
                        collectRoles(controller.getAnnotation(PreAuthorize.class), roles);
                        for (Method method : controller.getDeclaredMethods()) {
                                collectRoles(method.getAnnotation(PreAuthorize.class), roles);
                        }
                }
                return roles;
        }

        private void collectRoles(PreAuthorize annotation, Set<String> roles) {
                if (annotation == null) {
                        return;
                }
                Matcher matcher = ROLE_LITERAL.matcher(annotation.value());
                while (matcher.find()) {
                        roles.add(matcher.group(1));
                }
        }

        private List<Class<?>> adminControllers() {
                ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(
                                false);
                scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

                return scanner.findCandidateComponents(ADMIN_CONTROLLER_PACKAGE).stream()
                                .<Class<?>>map(definition -> ClassUtils.resolveClassName(definition.getBeanClassName(),
                                                null))
                                .toList();
        }

        private String methodAuthorization(Class<?> controller, String methodName) {
                return Arrays.stream(controller.getDeclaredMethods())
                                .filter(method -> method.getName().equals(methodName))
                                .findFirst()
                                .map(method -> method.getAnnotation(PreAuthorize.class))
                                .map(PreAuthorize::value)
                                .orElse(null);
        }

        @Test
        void roleHierarchy_adminImpliesRankingHeadAndRanking() {
                assertThat(reachableFrom("ROLE_ADMIN"))
                                .extracting(GrantedAuthority::getAuthority)
                                .contains("ROLE_ADMIN", "ROLE_RANKING_HEAD", "ROLE_RANKING");
        }

        @Test
        void roleHierarchy_rankingHeadImpliesRanking() {
                assertThat(reachableFrom("ROLE_RANKING_HEAD"))
                                .extracting(GrantedAuthority::getAuthority)
                                .contains("ROLE_RANKING_HEAD", "ROLE_RANKING")
                                .doesNotContain("ROLE_ADMIN");
        }

        @Test
        void roleHierarchy_rankingHasNoImpliedRoles() {
                assertThat(reachableFrom("ROLE_RANKING"))
                                .extracting(GrantedAuthority::getAuthority)
                                .containsExactly("ROLE_RANKING");
        }

        private Collection<? extends GrantedAuthority> reachableFrom(String authority) {
                RoleHierarchy hierarchy = new SecurityConfig().roleHierarchy();
                return hierarchy.getReachableGrantedAuthorities(List.of(new SimpleGrantedAuthority(authority)));
        }
}
