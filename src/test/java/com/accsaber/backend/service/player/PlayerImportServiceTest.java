package com.accsaber.backend.service.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.client.BeatLeaderClient;
import com.accsaber.backend.client.ScoreSaberClient;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.model.dto.platform.beatleader.BeatLeaderPlayerResponse;
import com.accsaber.backend.model.dto.platform.scoresaber.ScoreSaberPlayerResponse;
import com.accsaber.backend.model.entity.user.User;

@ExtendWith(MockitoExtension.class)
class PlayerImportServiceTest {

    private static final Long STEAM_ID = 76561198012345678L;

    @Mock
    private BeatLeaderClient beatLeaderClient;

    @Mock
    private ScoreSaberClient scoreSaberClient;

    @Mock
    private UserService userService;

    @InjectMocks
    private PlayerImportService playerImportService;

    @Nested
    class EnsurePlayerExists {

        @Test
        void returnsExistingUser_withoutApiCalls() {
            User existing = User.builder().id(STEAM_ID).name("Existing").build();
            when(userService.findOptionalBySteamId(STEAM_ID)).thenReturn(Optional.of(existing));

            User result = playerImportService.ensurePlayerExists(STEAM_ID);

            assertThat(result).isSameAs(existing);
            verifyNoInteractions(beatLeaderClient, scoreSaberClient);
            verify(userService, never()).createUser(anyLong(), anyString(), anyString(), anyString());
        }

        @Test
        void createsUser_withBlNameAndAvatar() {
            when(userService.findOptionalBySteamId(STEAM_ID)).thenReturn(Optional.empty());

            ScoreSaberPlayerResponse ssPlayer = new ScoreSaberPlayerResponse();
            ssPlayer.setId(String.valueOf(STEAM_ID));
            ssPlayer.setName("SSPlayer");
            ssPlayer.setProfilePicture("https://ss.com/avatar.png");
            ssPlayer.setCountry("US");
            when(scoreSaberClient.getPlayer(String.valueOf(STEAM_ID))).thenReturn(Optional.of(ssPlayer));

            BeatLeaderPlayerResponse blPlayer = new BeatLeaderPlayerResponse();
            blPlayer.setId(String.valueOf(STEAM_ID));
            blPlayer.setName("BLPlayer");
            blPlayer.setAvatar("https://bl.com/avatar.png");
            blPlayer.setCountry("DE");
            when(beatLeaderClient.getPlayer(String.valueOf(STEAM_ID))).thenReturn(Optional.of(blPlayer));

            User created = User.builder().id(STEAM_ID).name("BLPlayer").build();
            when(userService.createUser(STEAM_ID, "BLPlayer", "https://bl.com/avatar.png", "DE"))
                    .thenReturn(created);

            User result = playerImportService.ensurePlayerExists(STEAM_ID);

            assertThat(result).isSameAs(created);
            verify(userService).createUser(STEAM_ID, "BLPlayer", "https://bl.com/avatar.png", "DE");
        }

        @Test
        void fallsBackToSsData_whenBlUnavailable() {
            when(userService.findOptionalBySteamId(STEAM_ID)).thenReturn(Optional.empty());
            when(beatLeaderClient.getPlayer(String.valueOf(STEAM_ID))).thenReturn(Optional.empty());

            ScoreSaberPlayerResponse ssPlayer = new ScoreSaberPlayerResponse();
            ssPlayer.setName("SSOnly");
            ssPlayer.setProfilePicture("https://ss.com/avatar.png");
            ssPlayer.setCountry("JP");
            when(scoreSaberClient.getPlayer(String.valueOf(STEAM_ID))).thenReturn(Optional.of(ssPlayer));

            User created = User.builder().id(STEAM_ID).name("SSOnly").build();
            when(userService.createUser(STEAM_ID, "SSOnly", "https://ss.com/avatar.png", "JP"))
                    .thenReturn(created);

            User result = playerImportService.ensurePlayerExists(STEAM_ID);

            assertThat(result.getName()).isEqualTo("SSOnly");
        }

        @Test
        void usesUnknownName_whenBothPlatforms404() {
            when(userService.findOptionalBySteamId(STEAM_ID)).thenReturn(Optional.empty());
            when(scoreSaberClient.getPlayer(anyString())).thenReturn(Optional.empty());
            when(beatLeaderClient.getPlayer(anyString())).thenReturn(Optional.empty());

            User created = User.builder().id(STEAM_ID).name("Unknown").build();
            when(userService.createUser(STEAM_ID, "Unknown", null, null)).thenReturn(created);

            User result = playerImportService.ensurePlayerExists(STEAM_ID);

            assertThat(result.getName()).isEqualTo("Unknown");
        }

        @Test
        void returnsExistingUser_onConcurrentCreate() {
            User existing = User.builder().id(STEAM_ID).name("Existing").build();
            when(userService.findOptionalBySteamId(STEAM_ID))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(existing));
            when(scoreSaberClient.getPlayer(anyString())).thenReturn(Optional.empty());
            when(beatLeaderClient.getPlayer(anyString())).thenReturn(Optional.empty());
            when(userService.createUser(anyLong(), anyString(), any(), any()))
                    .thenThrow(new ConflictException("User", STEAM_ID));

            User result = playerImportService.ensurePlayerExists(STEAM_ID);

            assertThat(result).isSameAs(existing);
        }
    }

    @Nested
    class RefreshPlayerProfile {

        @Test
        void updatesProfile_withMergedData() {
            ScoreSaberPlayerResponse ssPlayer = new ScoreSaberPlayerResponse();
            ssPlayer.setName("UpdatedName");
            ssPlayer.setProfilePicture("https://ss.com/new.png");
            ssPlayer.setCountry("CA");
            when(scoreSaberClient.getPlayer(String.valueOf(STEAM_ID))).thenReturn(Optional.of(ssPlayer));
            when(beatLeaderClient.getPlayer(String.valueOf(STEAM_ID))).thenReturn(Optional.empty());

            playerImportService.refreshPlayerProfile(STEAM_ID);

            verify(userService).updateProfile(STEAM_ID, "UpdatedName", "https://ss.com/new.png", "CA");
        }

        @Test
        void skipsUpdate_whenBothPlatforms404() {
            when(scoreSaberClient.getPlayer(anyString())).thenReturn(Optional.empty());
            when(beatLeaderClient.getPlayer(anyString())).thenReturn(Optional.empty());

            playerImportService.refreshPlayerProfile(STEAM_ID);

            verify(userService, never()).updateProfile(anyLong(), anyString(), anyString(), anyString());
        }
    }
}
