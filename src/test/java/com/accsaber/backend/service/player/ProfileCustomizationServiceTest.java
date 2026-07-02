package com.accsaber.backend.service.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.user.PinnedScoreEntry;
import com.accsaber.backend.model.entity.score.Score;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserPinnedScore;
import com.accsaber.backend.model.entity.user.UserSettingKey;
import com.accsaber.backend.repository.score.ScoreRepository;
import com.accsaber.backend.repository.user.UserPinnedScoreRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.supporter.SupporterService;

@ExtendWith(MockitoExtension.class)
class ProfileCustomizationServiceTest {

    private static final Long USER_ID = 76561198000000001L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPinnedScoreRepository pinnedScoreRepository;

    @Mock
    private ScoreRepository scoreRepository;

    @Mock
    private UserService userService;

    @Mock
    private UserSettingsService userSettingsService;

    @Mock
    private RichTextSanitizer richTextSanitizer;

    @Mock
    private SupporterService supporterService;

    @InjectMocks
    private ProfileCustomizationService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(USER_ID).name("Old").build();
    }

    @Nested
    class UpdateName {

        @Test
        void changesNameAndDisablesSync() {
            when(userRepository.findByIdAndActiveTrue(USER_ID)).thenReturn(Optional.of(user));

            service.updateName(USER_ID, "Brand New");

            verify(userService).updateProfile(USER_ID, "Brand New", null, null, null);
            verify(userSettingsService).set(USER_ID, UserSettingKey.SYNC_NAME, false);
        }

        @Test
        void noopWhenNameUnchanged() {
            user.setName("Same");
            when(userRepository.findByIdAndActiveTrue(USER_ID)).thenReturn(Optional.of(user));

            service.updateName(USER_ID, "Same");

            verify(userService, never()).updateProfile(any(), any(), any(), any(), any());
            verify(userSettingsService, never()).set(any(), any(), any());
        }

        @Test
        void rejectsBlankName() {
            assertThatThrownBy(() -> service.updateName(USER_ID, "  "))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void rejectsOversizedName() {
            String huge = "x".repeat(ProfileCustomizationService.MAX_NAME_LENGTH + 1);
            assertThatThrownBy(() -> service.updateName(USER_ID, huge))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    class UpdateBio {

        @Test
        void sanitizesAndPersists() {
            when(userRepository.findByIdAndActiveTrue(USER_ID)).thenReturn(Optional.of(user));
            when(richTextSanitizer.sanitize("<p>hi</p>", 4000)).thenReturn("<p>hi</p>");

            service.updateBio(USER_ID, "<p>hi</p>");

            assertThat(user.getBio()).isEqualTo("<p>hi</p>");
            verify(userRepository).save(user);
        }
    }

    @Nested
    class UpdatePinnedScores {

        @Test
        void replacesPinsAtomically() {
            UUID scoreId = UUID.randomUUID();
            Score score = Score.builder().id(scoreId).user(user).active(true).build();
            when(userRepository.findByIdAndActiveTrue(USER_ID)).thenReturn(Optional.of(user));
            when(scoreRepository.findByIdWithUser(scoreId)).thenReturn(Optional.of(score));

            service.updatePinnedScores(USER_ID, List.of(new PinnedScoreEntry(scoreId, 0, null)));

            verify(pinnedScoreRepository).deleteByUser_Id(USER_ID);
            ArgumentCaptor<List<UserPinnedScore>> captor = ArgumentCaptor.captor();
            verify(pinnedScoreRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).hasSize(1);
            assertThat(captor.getValue().get(0).getDisplayOrder()).isZero();
            assertThat(captor.getValue().get(0).getScore()).isEqualTo(score);
        }

        @Test
        void allowsClearingByEmptyList() {
            when(userRepository.findByIdAndActiveTrue(USER_ID)).thenReturn(Optional.of(user));

            service.updatePinnedScores(USER_ID, List.of());

            verify(pinnedScoreRepository).deleteByUser_Id(USER_ID);
            verify(pinnedScoreRepository).saveAll(any());
        }

        @Test
        void rejectsMoreThanMax() {
            List<PinnedScoreEntry> tooMany = List.of(
                    new PinnedScoreEntry(UUID.randomUUID(), 0, null),
                    new PinnedScoreEntry(UUID.randomUUID(), 1, null),
                    new PinnedScoreEntry(UUID.randomUUID(), 2, null),
                    new PinnedScoreEntry(UUID.randomUUID(), 3, null));
            assertThatThrownBy(() -> service.updatePinnedScores(USER_ID, tooMany))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("at most");
        }

        @Test
        void rejectsDuplicateScoreId() {
            UUID id = UUID.randomUUID();
            List<PinnedScoreEntry> dup = List.of(
                    new PinnedScoreEntry(id, 0, null),
                    new PinnedScoreEntry(id, 1, null));
            assertThatThrownBy(() -> service.updatePinnedScores(USER_ID, dup))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("duplicate scoreId");
        }

        @Test
        void rejectsDuplicateOrder() {
            List<PinnedScoreEntry> dup = List.of(
                    new PinnedScoreEntry(UUID.randomUUID(), 0, null),
                    new PinnedScoreEntry(UUID.randomUUID(), 0, null));
            assertThatThrownBy(() -> service.updatePinnedScores(USER_ID, dup))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("duplicate displayOrder");
        }

        @Test
        void persistsComment() {
            UUID scoreId = UUID.randomUUID();
            Score score = Score.builder().id(scoreId).user(user).active(true).build();
            when(userRepository.findByIdAndActiveTrue(USER_ID)).thenReturn(Optional.of(user));
            when(scoreRepository.findByIdWithUser(scoreId)).thenReturn(Optional.of(score));

            service.updatePinnedScores(USER_ID,
                    List.of(new PinnedScoreEntry(scoreId, 0, "  My first 1k!  ")));

            ArgumentCaptor<List<UserPinnedScore>> captor = ArgumentCaptor.captor();
            verify(pinnedScoreRepository).saveAll(captor.capture());
            assertThat(captor.getValue().get(0).getComment()).isEqualTo("My first 1k!");
        }

        @Test
        void treatsBlankCommentAsNull() {
            UUID scoreId = UUID.randomUUID();
            Score score = Score.builder().id(scoreId).user(user).active(true).build();
            when(userRepository.findByIdAndActiveTrue(USER_ID)).thenReturn(Optional.of(user));
            when(scoreRepository.findByIdWithUser(scoreId)).thenReturn(Optional.of(score));

            service.updatePinnedScores(USER_ID,
                    List.of(new PinnedScoreEntry(scoreId, 0, "   ")));

            ArgumentCaptor<List<UserPinnedScore>> captor = ArgumentCaptor.captor();
            verify(pinnedScoreRepository).saveAll(captor.capture());
            assertThat(captor.getValue().get(0).getComment()).isNull();
        }

        @Test
        void rejectsOversizedComment() {
            UUID scoreId = UUID.randomUUID();
            Score score = Score.builder().id(scoreId).user(user).active(true).build();
            when(userRepository.findByIdAndActiveTrue(USER_ID)).thenReturn(Optional.of(user));
            when(scoreRepository.findByIdWithUser(scoreId)).thenReturn(Optional.of(score));

            String huge = "x".repeat(ProfileCustomizationService.MAX_PIN_COMMENT_LENGTH + 1);
            assertThatThrownBy(() -> service.updatePinnedScores(USER_ID,
                    List.of(new PinnedScoreEntry(scoreId, 0, huge))))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("comment");
        }

        @Test
        void rejectsForeignScore() {
            UUID scoreId = UUID.randomUUID();
            User otherUser = User.builder().id(99L).build();
            Score foreign = Score.builder().id(scoreId).user(otherUser).active(true).build();
            when(userRepository.findByIdAndActiveTrue(USER_ID)).thenReturn(Optional.of(user));
            when(scoreRepository.findByIdWithUser(scoreId)).thenReturn(Optional.of(foreign));

            assertThatThrownBy(() -> service.updatePinnedScores(USER_ID,
                    List.of(new PinnedScoreEntry(scoreId, 0, null))))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("does not belong");
        }

        @Test
        void rejectsInactiveScore() {
            UUID scoreId = UUID.randomUUID();
            Score inactive = Score.builder().id(scoreId).user(user).active(false).build();
            when(userRepository.findByIdAndActiveTrue(USER_ID)).thenReturn(Optional.of(user));
            when(scoreRepository.findByIdWithUser(scoreId)).thenReturn(Optional.of(inactive));

            assertThatThrownBy(() -> service.updatePinnedScores(USER_ID,
                    List.of(new PinnedScoreEntry(scoreId, 0, null))))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("not active");
        }

        @Test
        void rejectsMissingScore() {
            UUID scoreId = UUID.randomUUID();
            when(userRepository.findByIdAndActiveTrue(USER_ID)).thenReturn(Optional.of(user));
            when(scoreRepository.findByIdWithUser(scoreId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updatePinnedScores(USER_ID,
                    List.of(new PinnedScoreEntry(scoreId, 0, null))))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("not found");
        }
    }
}
