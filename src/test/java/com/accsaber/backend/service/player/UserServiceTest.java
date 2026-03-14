package com.accsaber.backend.service.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.response.player.UserResponse;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long STEAM_ID = 76561198000000000L;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Nested
    class FindBySteamId {

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

            UserResponse response = userService.findBySteamId(STEAM_ID);

            assertThat(response.getId()).isEqualTo(STEAM_ID);
            assertThat(response.getName()).isEqualTo("TestPlayer");
            assertThat(response.getCountry()).isEqualTo("US");
        }

        @Test
        void throwsWhenNotFound() {
            when(userRepository.findByIdAndActiveTrue(STEAM_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.findBySteamId(STEAM_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class FindOptionalBySteamId {

        @Test
        void returnsUser_whenExists() {
            User user = User.builder().id(STEAM_ID).name("Player").build();
            when(userRepository.findByIdAndActiveTrue(STEAM_ID)).thenReturn(Optional.of(user));

            Optional<User> result = userService.findOptionalBySteamId(STEAM_ID);

            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("Player");
        }

        @Test
        void returnsEmpty_whenNotExists() {
            when(userRepository.findByIdAndActiveTrue(STEAM_ID)).thenReturn(Optional.empty());

            Optional<User> result = userService.findOptionalBySteamId(STEAM_ID);

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

            User result = userService.updateProfile(STEAM_ID, "New", null, "CA");

            assertThat(result.getName()).isEqualTo("New");
            assertThat(result.getAvatarUrl()).isEqualTo("old.png");
            assertThat(result.getCountry()).isEqualTo("CA");
        }

        @Test
        void throwsNotFound_whenUserDoesNotExist() {
            when(userRepository.findByIdAndActiveTrue(STEAM_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateProfile(STEAM_ID, "Name", null, null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
