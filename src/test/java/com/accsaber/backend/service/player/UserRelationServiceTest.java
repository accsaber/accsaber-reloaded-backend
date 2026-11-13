package com.accsaber.backend.service.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.dto.response.player.UserRelationCounts;
import com.accsaber.backend.model.dto.response.player.UserRelationResponse;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserRelation;
import com.accsaber.backend.model.entity.user.UserRelationType;
import com.accsaber.backend.model.entity.user.UserSettingKey;
import com.accsaber.backend.model.entity.user.Visibility;
import com.accsaber.backend.repository.user.UserRelationRepository;
import com.accsaber.backend.repository.user.UserRelationRepository.RelationCountView;
import com.accsaber.backend.repository.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserRelationServiceTest {

    private static final Long OLLIE = 1L;
    private static final Long SONIQUE = 2L;
    private static final Long VIEWER = 3L;

    @Mock
    private UserRelationRepository relationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSettingsService userSettingsService;

    @InjectMocks
    private UserRelationService service;

    private final Pageable pageable = PageRequest.of(0, 20);

    @Nested
    class IncomingRelationPrivacy {

        private UserRelation ollieFollowsSonique;

        @BeforeEach
        void setUp() {
            ollieFollowsSonique = relation(OLLIE, "Ollie", SONIQUE, UserRelationType.follower);
            lenient().when(relationRepository.findByTargetUser_IdAndTypeAndActiveTrue(SONIQUE,
                    UserRelationType.follower, pageable))
                    .thenReturn(new PageImpl<>(List.of(ollieFollowsSonique), pageable, 1));
        }

        @Test
        void publicFollowingList_exposesTheFollower() {
            visibility(Visibility.PUBLIC);

            UserRelationResponse response = firstOf(service.findByTarget(SONIQUE, UserRelationType.follower,
                    VIEWER, pageable));

            assertThat(response.isHidden()).isFalse();
            assertThat(response.getTargetUserId()).isEqualTo(OLLIE);
            assertThat(response.getTargetName()).isEqualTo("Ollie");
        }

        @Test
        void privateFollowingList_replacesTheFollowerWithAPlaceholder() {
            visibility(Visibility.PRIVATE);

            UserRelationResponse response = firstOf(service.findByTarget(SONIQUE, UserRelationType.follower,
                    VIEWER, pageable));

            assertThat(response.isHidden()).isTrue();
            assertThat(response.getTargetName()).isEqualTo("Hidden Follower");
            assertThat(response.getTargetUserId()).isNull();
            assertThat(response.getTargetAvatarUrl()).isNull();
            assertThat(response.getTargetCdnAvatarUrl()).isNull();
            assertThat(response.getTargetCountry()).isNull();
            assertThat(response.getUserId()).isEqualTo(SONIQUE);
        }

        @Test
        void privateRivalList_saysHiddenRival() {
            UserRelation ollieRivalsSonique = relation(OLLIE, "Ollie", SONIQUE, UserRelationType.rival);
            when(relationRepository.findByTargetUser_IdAndTypeAndActiveTrue(SONIQUE, UserRelationType.rival,
                    pageable)).thenReturn(new PageImpl<>(List.of(ollieRivalsSonique), pageable, 1));
            when(userSettingsService.getMany(anyCollection(), eq(UserSettingKey.PRIVACY_RIVALS_VISIBILITY),
                    eq(Visibility.class))).thenReturn(Map.of(OLLIE, Visibility.PRIVATE));

            UserRelationResponse response = firstOf(service.findByTarget(SONIQUE, UserRelationType.rival,
                    VIEWER, pageable));

            assertThat(response.isHidden()).isTrue();
            assertThat(response.getTargetName()).isEqualTo("Hidden Rival");
        }

        @Test
        void privateFollowingList_stillShowsTheFollowerToThemselves() {
            UserRelationResponse response = firstOf(service.findByTarget(SONIQUE, UserRelationType.follower,
                    OLLIE, pageable));

            assertThat(response.isHidden()).isFalse();
            assertThat(response.getTargetUserId()).isEqualTo(OLLIE);
        }

        @Test
        void followersOnlyList_exposesTheFollowerToSomeoneTheyFollow() {
            visibility(Visibility.FOLLOWERS_ONLY);
            when(relationRepository.findActiveTargetUserIdsIn(eq(VIEWER), eq(UserRelationType.follower),
                    anyCollection())).thenReturn(List.of(OLLIE));

            UserRelationResponse response = firstOf(service.findByTarget(SONIQUE, UserRelationType.follower,
                    VIEWER, pageable));

            assertThat(response.isHidden()).isFalse();
        }

        @Test
        void followersOnlyList_hidesTheFollowerFromAnonymousViewers() {
            visibility(Visibility.FOLLOWERS_ONLY);

            UserRelationResponse response = firstOf(service.findByTarget(SONIQUE, UserRelationType.follower,
                    null, pageable));

            assertThat(response.isHidden()).isTrue();
        }

        private void visibility(Visibility visibility) {
            when(userSettingsService.getMany(anyCollection(), eq(UserSettingKey.PRIVACY_FOLLOWING_VISIBILITY),
                    eq(Visibility.class))).thenReturn(Map.of(OLLIE, visibility));
        }
    }

    @Nested
    class Counts {

        @BeforeEach
        void setUp() {
            List<RelationCountView> rows = List.of(
                    row(UserRelationType.follower, 4, 7),
                    row(UserRelationType.rival, 2, 3),
                    row(UserRelationType.blocked, 5, 0));
            when(relationRepository.countActiveByType(SONIQUE)).thenReturn(rows);
        }

        @Test
        void anonymousViewer_getsOnlyWhatPrivacyAllows() {
            visibility(Visibility.PRIVATE, Visibility.FOLLOWERS_ONLY);

            UserRelationCounts counts = service.countsFor(SONIQUE, null);

            assertThat(counts.getFollowerCount()).isEqualTo(7);
            assertThat(counts.getRivaledByCount()).isEqualTo(3);
            assertThat(counts.getFollowingCount()).isNull();
            assertThat(counts.getRivalCount()).isNull();
            assertThat(counts.getBlockedCount()).isNull();
        }

        @Test
        void follower_seesFollowersOnlyCounts() {
            visibility(Visibility.PRIVATE, Visibility.FOLLOWERS_ONLY);
            when(relationRepository.existsByUser_IdAndTargetUser_IdAndTypeAndActiveTrue(VIEWER, SONIQUE,
                    UserRelationType.follower)).thenReturn(true);

            UserRelationCounts counts = service.countsFor(SONIQUE, VIEWER);

            assertThat(counts.getFollowingCount()).isNull();
            assertThat(counts.getRivalCount()).isEqualTo(2);
            assertThat(counts.getBlockedCount()).isNull();
        }

        @Test
        void owner_seesEverythingWithoutReadingSettings() {
            UserRelationCounts counts = service.countsFor(SONIQUE, SONIQUE);

            assertThat(counts.getFollowingCount()).isEqualTo(4);
            assertThat(counts.getRivalCount()).isEqualTo(2);
            assertThat(counts.getBlockedCount()).isEqualTo(5);
            verifyNoInteractions(userSettingsService);
        }

        private void visibility(Visibility following, Visibility rivals) {
            when(userSettingsService.get(eq(SONIQUE), anyCollection(), eq(Visibility.class))).thenReturn(Map.of(
                    UserSettingKey.PRIVACY_FOLLOWING_VISIBILITY, following,
                    UserSettingKey.PRIVACY_RIVALS_VISIBILITY, rivals));
        }

        private RelationCountView row(UserRelationType type, long outgoing, long incoming) {
            RelationCountView row = mock(RelationCountView.class);
            lenient().when(row.getType()).thenReturn(type);
            lenient().when(row.getOutgoing()).thenReturn(outgoing);
            lenient().when(row.getIncoming()).thenReturn(incoming);
            return row;
        }
    }

    private UserRelationResponse firstOf(Page<UserRelationResponse> page) {
        return page.getContent().get(0);
    }

    private UserRelation relation(Long userId, String name, Long targetUserId, UserRelationType type) {
        return UserRelation.builder()
                .id(UUID.randomUUID())
                .user(User.builder().id(userId).name(name).avatarUrl("https://avatar/" + userId)
                        .cdnAvatarUrl("https://cdn/" + userId).country("US").build())
                .targetUser(User.builder().id(targetUserId).name("Sonique").build())
                .type(type)
                .active(true)
                .build();
    }
}
