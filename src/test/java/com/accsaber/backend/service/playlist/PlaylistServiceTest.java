package com.accsaber.backend.service.playlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

    private static final Long SNIPER_ID = 76561198000000001L;
    private static final Long TARGET_ID = 76561198000000002L;
    private static final String AVATAR = "https://avatars.example/target.png";
    private static final String SYNC_URL = "https://accsaber.test/v1/playlists/snipe/1/2?size=20";

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private MapDifficultyRepository mapDifficultyRepository;
    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PlaylistAssembler playlistAssembler;

    @InjectMocks
    private PlaylistService playlistService;

    @Nested
    class GenerateSnipePlaylist {

        @Test
        void buildsPlaylistFromTargetScoreDifficulties() {
            User target = userWith(TARGET_ID, "Victim", AVATAR);
            mockUsersExist(target);

            MapDifficulty diffA = MapDifficulty.builder().id(UUID.randomUUID()).build();
            MapDifficulty diffB = MapDifficulty.builder().id(UUID.randomUUID()).build();
            Page<Object[]> page = new PageImpl<>(List.<Object[]>of(
                    new Object[] { scoreOn(diffA), scoreOn(diffA) },
                    new Object[] { scoreOn(diffB), scoreOn(diffB) }));
            when(scoreRepository.findClosestSnipePairs(eq(SNIPER_ID), eq(TARGET_ID), isNull(), eq(false),
                    any(Pageable.class))).thenReturn(page);
            when(playlistAssembler.fetchAndEncodeImage(AVATAR)).thenReturn("data:image/png;base64,XXX");
            Map<String, Object> assembled = Map.of("playlistTitle", "Snipe Victim");
            when(playlistAssembler.assemble(eq("Snipe Victim"), eq("data:image/png;base64,XXX"),
                    eq(SYNC_URL), any())).thenReturn(assembled);

            Map<String, Object> result = playlistService.generateSnipePlaylist(SNIPER_ID, TARGET_ID, null, 20, SYNC_URL);

            assertThat(result).isSameAs(assembled);
            ArgumentCaptor<List<MapDifficulty>> diffCaptor = captureDifficulties();
            verify(playlistAssembler).assemble(anyString(), anyString(), anyString(), diffCaptor.capture());
            assertThat(diffCaptor.getValue()).containsExactly(diffA, diffB);
        }

        @Test
        void appendsCategoryLabelToTitle() {
            mockUsersExist(userWith(TARGET_ID, "Victim", AVATAR));
            UUID catId = UUID.randomUUID();
            when(categoryRepository.findByCodeAndActiveTrue("true_acc"))
                    .thenReturn(Optional.of(Category.builder().id(catId).code("true_acc").name("True Acc").build()));
            when(scoreRepository.findClosestSnipePairs(eq(SNIPER_ID), eq(TARGET_ID), eq(catId), eq(false),
                    any(Pageable.class))).thenReturn(new PageImpl<>(List.<Object[]>of()));
            when(playlistAssembler.assemble(eq("Snipe Victim (True Acc)"), any(), anyString(), any()))
                    .thenReturn(Map.of());

            playlistService.generateSnipePlaylist(SNIPER_ID, TARGET_ID, "true_acc", 20, SYNC_URL);

            verify(playlistAssembler).assemble(eq("Snipe Victim (True Acc)"), any(), anyString(), any());
        }

        @Test
        void overallCategoryFlipsFlag() {
            mockUsersExist(userWith(TARGET_ID, "Victim", AVATAR));
            when(categoryRepository.findByCodeAndActiveTrue("overall"))
                    .thenReturn(Optional.of(Category.builder().code("overall").name("Overall").build()));
            when(scoreRepository.findClosestSnipePairs(eq(SNIPER_ID), eq(TARGET_ID), isNull(), eq(true),
                    any(Pageable.class))).thenReturn(new PageImpl<>(List.<Object[]>of()));
            when(playlistAssembler.assemble(anyString(), any(), anyString(), any())).thenReturn(Map.of());

            playlistService.generateSnipePlaylist(SNIPER_ID, TARGET_ID, "overall", 20, SYNC_URL);

            verify(scoreRepository).findClosestSnipePairs(eq(SNIPER_ID), eq(TARGET_ID), isNull(), eq(true),
                    any(Pageable.class));
        }

        @Test
        void capsSizeAtMax() {
            mockUsersExist(userWith(TARGET_ID, "Victim", AVATAR));
            when(scoreRepository.findClosestSnipePairs(eq(SNIPER_ID), eq(TARGET_ID), any(), anyBoolean(),
                    argThat(p -> p.getPageSize() == PlaylistService.SNIPE_PLAYLIST_MAX_SONGS)))
                    .thenReturn(new PageImpl<>(List.<Object[]>of()));
            when(playlistAssembler.assemble(anyString(), any(), anyString(), any())).thenReturn(Map.of());

            playlistService.generateSnipePlaylist(SNIPER_ID, TARGET_ID, null, 999, SYNC_URL);

            verify(scoreRepository).findClosestSnipePairs(eq(SNIPER_ID), eq(TARGET_ID), any(), anyBoolean(),
                    argThat(p -> p.getPageSize() == PlaylistService.SNIPE_PLAYLIST_MAX_SONGS));
        }

        @Test
        void clampsSizeBelowOneToOne() {
            mockUsersExist(userWith(TARGET_ID, "Victim", AVATAR));
            when(scoreRepository.findClosestSnipePairs(eq(SNIPER_ID), eq(TARGET_ID), any(), anyBoolean(),
                    argThat(p -> p.getPageSize() == 1)))
                    .thenReturn(new PageImpl<>(List.<Object[]>of()));
            when(playlistAssembler.assemble(anyString(), any(), anyString(), any())).thenReturn(Map.of());

            playlistService.generateSnipePlaylist(SNIPER_ID, TARGET_ID, null, 0, SYNC_URL);

            verify(scoreRepository).findClosestSnipePairs(eq(SNIPER_ID), eq(TARGET_ID), any(), anyBoolean(),
                    argThat(p -> p.getPageSize() == 1));
        }

        @Test
        void emptyResultStillProducesPlaylistWithNoSongs() {
            mockUsersExist(userWith(TARGET_ID, "Victim", AVATAR));
            when(scoreRepository.findClosestSnipePairs(eq(SNIPER_ID), eq(TARGET_ID), any(), anyBoolean(),
                    any(Pageable.class))).thenReturn(new PageImpl<>(List.<Object[]>of()));
            when(playlistAssembler.assemble(anyString(), any(), anyString(), any())).thenReturn(Map.of());

            playlistService.generateSnipePlaylist(SNIPER_ID, TARGET_ID, null, 20, SYNC_URL);

            ArgumentCaptor<List<MapDifficulty>> diffCaptor = captureDifficulties();
            verify(playlistAssembler).assemble(anyString(), any(), anyString(), diffCaptor.capture());
            assertThat(diffCaptor.getValue()).isEmpty();
        }

        @Test
        void rejectsSelfSnipe() {
            assertThatThrownBy(() -> playlistService.generateSnipePlaylist(SNIPER_ID, SNIPER_ID, null, 20, SYNC_URL))
                    .isInstanceOf(ValidationException.class);
            verify(userRepository, never()).findByIdAndActiveTrue(any());
            verify(scoreRepository, never()).findClosestSnipePairs(any(), any(), any(), anyBoolean(), any());
        }

        @Test
        void throwsWhenTargetMissing() {
            when(userRepository.findByIdAndActiveTrue(SNIPER_ID))
                    .thenReturn(Optional.of(User.builder().id(SNIPER_ID).build()));
            when(userRepository.findByIdAndActiveTrue(TARGET_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> playlistService.generateSnipePlaylist(SNIPER_ID, TARGET_ID, null, 20, SYNC_URL))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void unknownCategoryThrows() {
            mockUsersExist(userWith(TARGET_ID, "Victim", AVATAR));
            when(categoryRepository.findByCodeAndActiveTrue("nope")).thenReturn(Optional.empty());

            assertThatThrownBy(
                    () -> playlistService.generateSnipePlaylist(SNIPER_ID, TARGET_ID, "nope", 20, SYNC_URL))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        private void mockUsersExist(User target) {
            when(userRepository.findByIdAndActiveTrue(SNIPER_ID))
                    .thenReturn(Optional.of(User.builder().id(SNIPER_ID).build()));
            when(userRepository.findByIdAndActiveTrue(TARGET_ID)).thenReturn(Optional.of(target));
        }

        @SuppressWarnings("unchecked")
        private ArgumentCaptor<List<MapDifficulty>> captureDifficulties() {
            return (ArgumentCaptor<List<MapDifficulty>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
        }

        private User userWith(Long id, String name, String avatar) {
            return User.builder().id(id).name(name).avatarUrl(avatar).build();
        }

        private Score scoreOn(MapDifficulty diff) {
            return Score.builder().id(UUID.randomUUID()).mapDifficulty(diff).build();
        }
    }
}
