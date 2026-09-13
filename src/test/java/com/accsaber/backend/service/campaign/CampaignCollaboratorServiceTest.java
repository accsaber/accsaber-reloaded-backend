package com.accsaber.backend.service.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.entity.campaign.CampaignCollaboratorStatus;
import com.accsaber.backend.repository.campaign.CampaignCollaboratorRepository;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;

@ExtendWith(MockitoExtension.class)
class CampaignCollaboratorServiceTest {

    @Mock
    private CampaignCollaboratorRepository collaboratorRepository;
    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DuplicateUserService duplicateUserService;

    @InjectMocks
    private CampaignCollaboratorService service;

    private UUID campaignId;

    @BeforeEach
    void setUp() {
        campaignId = UUID.randomUUID();
    }

    @Nested
    class IsParticipant {

        @Test
        void ownerCountsWithoutACollaboratorLookup() {
            when(campaignRepository.findCreatorIdByIdAndActiveTrue(campaignId)).thenReturn(Optional.of(50L));

            assertThat(service.isParticipant(campaignId, 50L)).isTrue();
            verify(collaboratorRepository, never())
                    .existsByCampaign_IdAndUser_IdAndStatusAndActiveTrue(any(), anyLong(), any());
        }

        @Test
        void acceptedCollaboratorCounts() {
            when(campaignRepository.findCreatorIdByIdAndActiveTrue(campaignId)).thenReturn(Optional.of(99L));
            when(collaboratorRepository.existsByCampaign_IdAndUser_IdAndStatusAndActiveTrue(
                    campaignId, 50L, CampaignCollaboratorStatus.ACCEPTED)).thenReturn(true);

            assertThat(service.isParticipant(campaignId, 50L)).isTrue();
        }

        @Test
        void anyoneElseDoesNot() {
            when(campaignRepository.findCreatorIdByIdAndActiveTrue(campaignId)).thenReturn(Optional.of(99L));

            assertThat(service.isParticipant(campaignId, 50L)).isFalse();
        }

        @Test
        void inactiveCampaignHasNoOwnerToMatch() {
            when(campaignRepository.findCreatorIdByIdAndActiveTrue(campaignId)).thenReturn(Optional.empty());

            assertThat(service.isParticipant(campaignId, 50L)).isFalse();
        }
    }
}
