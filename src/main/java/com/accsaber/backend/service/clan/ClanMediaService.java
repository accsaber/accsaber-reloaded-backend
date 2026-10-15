package com.accsaber.backend.service.clan;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.accsaber.backend.config.CdnProperties;
import com.accsaber.backend.model.dto.request.clan.ModerateClanRequest;
import com.accsaber.backend.model.dto.response.clan.ClanResponse;
import com.accsaber.backend.model.entity.clan.ClanMedia;
import com.accsaber.backend.service.media.MediaFormat;
import com.accsaber.backend.service.media.MediaProcessingService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClanMediaService {

    private final ClanService clanService;
    private final MediaProcessingService mediaProcessingService;
    private final CdnProperties cdn;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ClanResponse upload(UUID clanId, Long playerId, ClanMedia media, MultipartFile file) {
        clanService.assertCanCustomize(clanId, playerId);
        String key = clanId.toString();
        String url = switch (media) {
            case icon -> mediaProcessingService.storeImage(file, media.getSubdir(), key, MediaFormat.GIF,
                    cdn.getAvatarMaxDimension());
            case banner -> mediaProcessingService.storeImage(file, media.getSubdir(), key);
        };
        return clanService.setMedia(clanId, playerId, media, url);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ClanResponse remove(UUID clanId, Long playerId, ClanMedia media) {
        ClanResponse clan = clanService.setMedia(clanId, playerId, media, null);
        mediaProcessingService.deleteIfExists(media.getSubdir(), clanId.toString());
        return clan;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ClanResponse moderate(UUID clanId, ModerateClanRequest request) {
        ClanResponse clan = clanService.moderate(clanId, request);
        request.removeMedia().forEach(media -> mediaProcessingService.deleteIfExists(media.getSubdir(),
                clanId.toString()));
        return clan;
    }
}
