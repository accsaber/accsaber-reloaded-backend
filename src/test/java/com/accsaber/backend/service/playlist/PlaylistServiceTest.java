package com.accsaber.backend.service.playlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.entity.Category;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.campaign.CampaignDifficulty;
import com.accsaber.backend.model.entity.map.MapDifficulty;
import com.accsaber.backend.model.entity.score.SnipeSort;
import com.accsaber.backend.model.entity.score.SnipeUnplayed;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.campaign.CampaignDifficultyRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.score.ScoreService;
import com.accsaber.backend.service.snipe.SnipeQuery;
import com.accsaber.backend.service.snipe.SnipeSelection;

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
    private UserRepository userRepository;
    @Mock
    private CampaignDifficultyRepository campaignDifficultyRepository;
    @Mock
    private ScoreService scoreService;
    @Mock
    private PlaylistAssembler playlistAssembler;

    @InjectMocks
    private PlaylistService playlistService;

    @Nested
    class GenerateCampaignPlaylist {

        @Test
        void buildsPlaylistFromCampaignDifficultiesExcludingBarriers() {
            UUID campaignId = UUID.randomUUID();
            Campaign campaign = Campaign.builder()
                    .id(campaignId)
                    .name("Acc Academy")
                    .iconUrl("https://cdn.example/icon.png")
                    .build();
            MapDifficulty diffA = MapDifficulty.builder().id(UUID.randomUUID()).build();
            MapDifficulty diffB = MapDifficulty.builder().id(UUID.randomUUID()).build();
            CampaignDifficulty node = CampaignDifficulty.builder().mapDifficulty(diffA).build();
            CampaignDifficulty barrier = CampaignDifficulty.builder().mapDifficulty(diffB).barrier(true).build();
            when(campaignDifficultyRepository.findActiveWithMapByCampaignId(campaignId))
                    .thenReturn(List.of(node, barrier));
            when(playlistAssembler.fetchAndEncodeImage("https://cdn.example/icon.png"))
                    .thenReturn("data:image/png;base64,ICON");
            Map<String, Object> assembled = Map.of("playlistTitle", "AccSaber Campaign: Acc Academy");
            when(playlistAssembler.assemble(eq("AccSaber Campaign: Acc Academy"), eq("data:image/png;base64,ICON"),
                    eq(SYNC_URL), any())).thenReturn(assembled);

            Map<String, Object> result = playlistService.generateCampaignPlaylist(campaign, SYNC_URL);

            assertThat(result).isSameAs(assembled);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<MapDifficulty>> diffCaptor = (ArgumentCaptor<List<MapDifficulty>>) (ArgumentCaptor<?>) ArgumentCaptor
                    .forClass(List.class);
            verify(playlistAssembler).assemble(anyString(), anyString(), anyString(), diffCaptor.capture());
            assertThat(diffCaptor.getValue()).containsExactly(diffA);
        }

        @Test
        void fallsBackToOverallImageWithoutIcon() {
            UUID campaignId = UUID.randomUUID();
            Campaign campaign = Campaign.builder().id(campaignId).name("No Icon").build();
            when(campaignDifficultyRepository.findActiveWithMapByCampaignId(campaignId)).thenReturn(List.of());
            when(playlistAssembler.loadCategoryImage("overall")).thenReturn("data:image/png;base64,OVERALL");
            when(playlistAssembler.assemble(anyString(), eq("data:image/png;base64,OVERALL"), anyString(), any()))
                    .thenReturn(Map.of());

            playlistService.generateCampaignPlaylist(campaign, SYNC_URL);

            verify(playlistAssembler, never()).fetchAndEncodeImage(any());
            verify(playlistAssembler).assemble(eq("AccSaber Campaign: No Icon"),
                    eq("data:image/png;base64,OVERALL"), eq(SYNC_URL), any());
        }
    }

    @Nested
    class GenerateUserScoresPlaylist {

        private final Pageable pageable = PageRequest.of(0, 25, Sort.by(Sort.Direction.ASC, "accuracy"));

        @Test
        void buildsPlaylistFromTheScoreListSlice() {
            when(userRepository.findByIdAndActiveTrue(TARGET_ID))
                    .thenReturn(Optional.of(User.builder().id(TARGET_ID).name("Player").avatarUrl(AVATAR).build()));
            MapDifficulty diffA = MapDifficulty.builder().id(UUID.randomUUID()).build();
            MapDifficulty diffB = MapDifficulty.builder().id(UUID.randomUUID()).build();
            when(scoreService.findDifficultiesByUser(TARGET_ID, null, null, pageable))
                    .thenReturn(List.of(diffA, diffB));
            when(playlistAssembler.fetchAndEncodeImage(AVATAR)).thenReturn("data:image/png;base64,XXX");
            Map<String, Object> assembled = Map.of("playlistTitle", "AccSaber Scores - Player");
            when(playlistAssembler.assemble(eq("AccSaber Scores - Player"), eq("data:image/png;base64,XXX"),
                    eq(SYNC_URL), any())).thenReturn(assembled);

            Map<String, Object> result = playlistService.generateUserScoresPlaylist(TARGET_ID, null, null, pageable,
                    SYNC_URL);

            assertThat(result).isSameAs(assembled);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<MapDifficulty>> diffCaptor = (ArgumentCaptor<List<MapDifficulty>>) (ArgumentCaptor<?>) ArgumentCaptor
                    .forClass(List.class);
            verify(playlistAssembler).assemble(anyString(), anyString(), anyString(), diffCaptor.capture());
            assertThat(diffCaptor.getValue()).containsExactly(diffA, diffB);
        }

        @Test
        void appendsCategoryLabelToTitleAndPassesFilterThrough() {
            when(userRepository.findByIdAndActiveTrue(TARGET_ID))
                    .thenReturn(Optional.of(User.builder().id(TARGET_ID).name("Player").avatarUrl(AVATAR).build()));
            UUID catId = UUID.randomUUID();
            when(scoreService.findDifficultiesByUser(TARGET_ID, catId, "camellia", pageable)).thenReturn(List.of());
            when(categoryRepository.findById(catId))
                    .thenReturn(Optional.of(Category.builder().id(catId).code("tech_acc").name("Tech Acc").build()));
            when(playlistAssembler.assemble(eq("AccSaber Scores - Player (Tech Acc)"), any(), eq(SYNC_URL), any()))
                    .thenReturn(Map.of());

            playlistService.generateUserScoresPlaylist(TARGET_ID, catId, "camellia", pageable, SYNC_URL);

            verify(scoreService).findDifficultiesByUser(TARGET_ID, catId, "camellia", pageable);
            verify(playlistAssembler).assemble(eq("AccSaber Scores - Player (Tech Acc)"), any(), eq(SYNC_URL), any());
        }

        @Test
        void throwsWhenUserMissing() {
            when(userRepository.findByIdAndActiveTrue(TARGET_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> playlistService.generateUserScoresPlaylist(TARGET_ID, null, null, pageable,
                    SYNC_URL)).isInstanceOf(ResourceNotFoundException.class);
            verify(playlistAssembler, never()).assemble(anyString(), any(), anyString(), any());
        }
    }

    @Nested
    class GenerateSnipePlaylist {

        @Test
        void buildsPlaylistFromTheSelectedDifficulties() {
            MapDifficulty diffA = MapDifficulty.builder().id(UUID.randomUUID()).build();
            MapDifficulty diffB = MapDifficulty.builder().id(UUID.randomUUID()).build();
            SnipeSelection selection = selectionOf(null, List.of(diffA, diffB));
            when(playlistAssembler.fetchAndEncodeImage(AVATAR)).thenReturn("data:image/png;base64,XXX");
            Map<String, Object> assembled = Map.of("playlistTitle", "AccSaber: Snipe Victim");
            when(playlistAssembler.assemble(eq("AccSaber: Snipe Victim"), eq("data:image/png;base64,XXX"),
                    eq(SYNC_URL), any())).thenReturn(assembled);

            Map<String, Object> result = playlistService.generateSnipePlaylist(selection, query(null, null), SYNC_URL);

            assertThat(result).isSameAs(assembled);
            ArgumentCaptor<List<MapDifficulty>> diffCaptor = captureDifficulties();
            verify(playlistAssembler).assemble(anyString(), anyString(), anyString(), diffCaptor.capture());
            assertThat(diffCaptor.getValue()).containsExactly(diffA, diffB);
        }

        static Stream<Arguments> titledSelections() {
            return Stream.of(
                    Arguments.of("AccSaber: Snipe Victim (True Acc)", "True Acc", null, null),
                    Arguments.of("AccSaber: Snipe Victim - AP gap high to low", null, SnipeSort.AP_GAP, null),
                    Arguments.of("AccSaber: Snipe Victim", null, SnipeSort.GAP, null),
                    Arguments.of("AccSaber: Snipe Victim - unplayed only", null, null, SnipeUnplayed.ONLY));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("titledSelections")
        void labelsTheTitleFromTheSelection(String expectedTitle, String categoryLabel, SnipeSort sort,
                SnipeUnplayed unplayed) {
            when(playlistAssembler.assemble(anyString(), any(), anyString(), any())).thenReturn(Map.of());

            playlistService.generateSnipePlaylist(selectionOf(categoryLabel, List.of()), query(sort, null, unplayed),
                    SYNC_URL);

            verify(playlistAssembler).assemble(eq(expectedTitle), any(), anyString(), any());
        }

        private SnipeSelection selectionOf(String categoryLabel, List<MapDifficulty> difficulties) {
            return new SnipeSelection(userWith(TARGET_ID, "Victim", AVATAR), categoryLabel, difficulties);
        }

        private SnipeQuery query(SnipeSort sort, Sort.Direction direction) {
            return query(sort, direction, null);
        }

        private SnipeQuery query(SnipeSort sort, Sort.Direction direction, SnipeUnplayed unplayed) {
            return new SnipeQuery(SNIPER_ID, TARGET_ID, null, sort, direction, unplayed);
        }

        @SuppressWarnings("unchecked")
        private ArgumentCaptor<List<MapDifficulty>> captureDifficulties() {
            return (ArgumentCaptor<List<MapDifficulty>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
        }

        private User userWith(Long id, String name, String avatar) {
            return User.builder().id(id).name(name).avatarUrl(avatar).build();
        }
    }
}
