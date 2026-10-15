package com.accsaber.backend.service.clan;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.accsaber.backend.config.CdnProperties;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.model.entity.clan.ClanMedia;
import com.accsaber.backend.service.media.MediaFormat;
import com.accsaber.backend.service.media.MediaProcessingService;

@ExtendWith(MockitoExtension.class)
class ClanMediaServiceTest {

    private static final UUID CLAN_ID = UUID.randomUUID();
    private static final Long PLAYER = 5L;
    private static final MockMultipartFile FILE = new MockMultipartFile("file", "a.png", "image/png", new byte[] {1});

    @Mock
    private ClanService clanService;
    @Mock
    private MediaProcessingService mediaProcessingService;
    @Spy
    private CdnProperties cdn = new CdnProperties();

    @InjectMocks
    private ClanMediaService service;

    @Test
    void aBannerIsAuthorisedBeforeItIsStoredAsWebp() {
        when(mediaProcessingService.storeImage(FILE, "clan-banners", CLAN_ID.toString())).thenReturn("url");

        service.upload(CLAN_ID, PLAYER, ClanMedia.banner, FILE);

        InOrder order = inOrder(clanService, mediaProcessingService);
        order.verify(clanService).assertCanCustomize(CLAN_ID, PLAYER);
        order.verify(mediaProcessingService).storeImage(FILE, "clan-banners", CLAN_ID.toString());
        order.verify(clanService).setMedia(CLAN_ID, PLAYER, ClanMedia.banner, "url");
    }

    @Test
    void anIconKeepsItsGifAndAvatarSize() {
        service.upload(CLAN_ID, PLAYER, ClanMedia.icon, FILE);

        verify(mediaProcessingService).storeImage(FILE, "clan-icons", CLAN_ID.toString(), MediaFormat.GIF,
                cdn.getAvatarMaxDimension());
    }

    @Test
    void anOutsiderNeverReachesTheCdn() {
        doThrow(new ForbiddenException("no")).when(clanService).assertCanCustomize(CLAN_ID, PLAYER);

        assertThatThrownBy(() -> service.upload(CLAN_ID, PLAYER, ClanMedia.banner, FILE))
                .isInstanceOf(ForbiddenException.class);
        verify(mediaProcessingService, never()).storeImage(any(), anyString(), anyString());
        verify(mediaProcessingService, never()).storeImage(any(), anyString(), anyString(), any(), anyInt());
    }
}
