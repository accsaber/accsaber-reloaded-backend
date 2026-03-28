package com.accsaber.backend.service.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.player.UserResponse;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserNameHistory;
import com.accsaber.backend.repository.user.UserNameHistoryRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.milestone.LevelService;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long STEAM_ID = 76561198000000000L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserNameHistoryRepository userNameHistoryRepository;

    @Mock
    private DuplicateUserService duplicateUserService;

    @Mock
    private LevelService levelService;

    @InjectMocks
    private UserService userService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(duplicateUserService.resolvePrimaryUserId(org.mockito.ArgumentMatchers.any(Long.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(levelService.calculateLevel(any()))
                .thenReturn(com.accsaber.backend.model.dto.response.milestone.LevelResponse.builder()
                        .level(0).title(null).build());
    }

    @Nested
    class FindByUserId {

        @Test
        void returnsUserResponse() {
            User user = User.builder()
                    .id(STEAM_ID)
                    .name("TestPlayer")
                    .avatarUrl("https://example.com/avatar.jpg")
                    .country("US")
                    .createdAt(Instant.now())
                    .build();

            when(userRepository.findByIdAndActiveTrue(STEAM_ID)).thenReturn(Optional.of(user));

            UserResponse response = userService.findByUserId(STEAM_ID);

            assertThat(response.getId()).isEqualTo(String.valueOf(STEAM_ID));
            assertThat(response.getName()).isEqualTo("TestPlayer");
            assertThat(response.getCountry()).isEqualTo("US");
        }

        @Test
        void throwsWhenNotFound() {
            when(userRepository.findByIdAndActiveTrue(STEAM_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.findByUserId(STEAM_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class FindOptionalByUserId {

        @Test
        void returnsUser_whenExists() {
            User user = User.builder().id(STEAM_ID).name("Player").build();
            when(userRepository.findByIdAndActiveTrue(STEAM_ID)).thenReturn(Optional.of(user));

            Optional<User> result = userService.findOptionalByUserId(STEAM_ID);

            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("Player");
        }

        @Test
        void returnsEmpty_whenNotExists() {
            when(userRepository.findByIdAndActiveTrue(STEAM_ID)).thenReturn(Optional.empty());

            Optional<User> result = userService.findOptionalByUserId(STEAM_ID);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class CreateUser {

        @Test
        void createsAndReturnsUser() {
            when(userRepository.findByIdAndActiveTrue(STEAM_ID)).thenReturn(Optional.empty());
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            User result = userService.createUser(STEAM_ID, "NewPlayer", "https://avatar.png", "US");

            assertThat(result.getId()).isEqualTo(STEAM_ID);
            assertThat(result.getName()).isEqualTo("NewPlayer");
            assertThat(result.getAvatarUrl()).isEqualTo("https://avatar.png");
            assertThat(result.getCountry()).isEqualTo("US");
        }

        @Test
        void throwsConflict_whenUserAlreadyExists() {
            User existing = User.builder().id(STEAM_ID).name("Existing").build();
            when(userRepository.findByIdAndActiveTrue(STEAM_ID)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> userService.createUser(STEAM_ID, "New", null, null))
                    .isInstanceOf(ConflictException.class);
        }
    }

    @Nested
    class UpdateProfile {

        @Test
        void updatesOnlyNonNullFields() {
            User user = User.builder()
                    .id(STEAM_ID).name("Old").avatarUrl("old.png").country("US").build();
            when(userRepository.findByIdAndActiveTrue(STEAM_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            User result = userService.updateProfile(STEAM_ID, "New", null, "CA", null);

            assertThat(result.getName()).isEqualTo("New");
            assertThat(result.getAvatarUrl()).isEqualTo("old.png");
            assertThat(result.getCountry()).isEqualTo("CA");
        }

        @Test
        void savesOldNameToHistory_whenNameChanges() {
            User user = User.builder()
                    .id(STEAM_ID).name("OldName").avatarUrl("avatar.png").country("US").build();
            when(userRepository.findByIdAndActiveTrue(STEAM_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            userService.updateProfile(STEAM_ID, "NewName", null, null, null);

            ArgumentCaptor<UserNameHistory> captor = ArgumentCaptor.forClass(UserNameHistory.class);
            verify(userNameHistoryRepository).save(captor.capture());
            UserNameHistory saved = captor.getValue();
            assertThat(saved.getName()).isEqualTo("OldName");
            assertThat(saved.getUser()).isEqualTo(user);
        }

        @Test
        void doesNotSaveHistory_whenNameUnchanged() {
            User user = User.builder()
                    .id(STEAM_ID).name("SameName").avatarUrl("avatar.png").country("US").build();
            when(userRepository.findByIdAndActiveTrue(STEAM_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            userService.updateProfile(STEAM_ID, "SameName", null, null, null);

            verify(userNameHistoryRepository, never()).save(any());
        }

        @Test
        void doesNotSaveHistory_whenNameIsNull() {
            User user = User.builder()
                    .id(STEAM_ID).name("CurrentName").avatarUrl("avatar.png").country("US").build();
            when(userRepository.findByIdAndActiveTrue(STEAM_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            userService.updateProfile(STEAM_ID, null, "new-avatar.png", null, null);

            verify(userNameHistoryRepository, never()).save(any());
            assertThat(user.getName()).isEqualTo("CurrentName");
        }

        @Test
        void throwsNotFound_whenUserDoesNotExist() {
            when(userRepository.findByIdAndActiveTrue(STEAM_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateProfile(STEAM_ID, "Name", null, null, null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class GetNameHistory {

        @Test
        void returnsHistoryOrderedByChangedAtDesc() {
            User user = User.builder().id(STEAM_ID).name("Current").build();
            UserNameHistory older = UserNameHistory.builder()
                    .user(user).name("First").changedAt(Instant.parse("2025-01-01T00:00:00Z")).build();
            UserNameHistory newer = UserNameHistory.builder()
                    .user(user).name("Second").changedAt(Instant.parse("2025-06-01T00:00:00Z")).build();

            when(userNameHistoryRepository.findByUser_IdOrderByChangedAtDesc(STEAM_ID))
                    .thenReturn(List.of(newer, older));

            List<UserNameHistory> result = userService.getNameHistory(STEAM_ID);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("Second");
            assertThat(result.get(1).getName()).isEqualTo("First");
        }

        @Test
        void returnsEmptyList_whenNoHistory() {
            when(userNameHistoryRepository.findByUser_IdOrderByChangedAtDesc(STEAM_ID))
                    .thenReturn(List.of());

            List<UserNameHistory> result = userService.getNameHistory(STEAM_ID);

            assertThat(result).isEmpty();
        }
    }
}
