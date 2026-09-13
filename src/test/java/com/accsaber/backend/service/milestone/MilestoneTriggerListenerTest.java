package com.accsaber.backend.service.milestone;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.accsaber.backend.model.dto.response.common.PlayerRef;
import com.accsaber.backend.model.entity.campaign.CampaignCollaboratorStatus;
import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;
import com.accsaber.backend.model.entity.market.MarketListingStatus;
import com.accsaber.backend.model.event.CampaignCompletedEvent;
import com.accsaber.backend.model.event.CampaignDistinctionEvent;
import com.accsaber.backend.model.event.InventoryChangedEvent;
import com.accsaber.backend.model.event.MarketListingEvent;
import com.accsaber.backend.repository.campaign.CampaignCollaboratorRepository;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.repository.campaign.UserCampaignRepository;
import com.accsaber.backend.service.milestone.source.MilestoneTrigger;

class MilestoneTriggerListenerTest {

    private static final Long USER_ID = 76561198000000001L;
    private static final Long OTHER_ID = 76561198000000002L;
    private static final Long THIRD_ID = 76561198000000003L;

    private MilestoneEvaluationService evaluationService;
    private CampaignRepository campaignRepository;
    private UserCampaignRepository userCampaignRepository;
    private CampaignCollaboratorRepository campaignCollaboratorRepository;

    @BeforeEach
    void setUp() {
        evaluationService = mock(MilestoneEvaluationService.class);
        campaignRepository = mock(CampaignRepository.class);
        userCampaignRepository = mock(UserCampaignRepository.class);
        campaignCollaboratorRepository = mock(CampaignCollaboratorRepository.class);
    }

    private MilestoneTriggerListener listener(long quietSeconds, long maxWaitSeconds) {
        return new MilestoneTriggerListener(evaluationService, campaignRepository, userCampaignRepository,
                campaignCollaboratorRepository, Runnable::run, quietSeconds, maxWaitSeconds);
    }

    private PlayerRef ref(Long id) {
        return new PlayerRef(String.valueOf(id), null, null, null, null);
    }

    @Test
    void burstOfInventoryChangesDispatchesOnceAfterQuietWindow() {
        MilestoneTriggerListener listener = listener(0, 0);

        listener.onInventoryChanged(new InventoryChangedEvent(USER_ID));
        listener.onInventoryChanged(new InventoryChangedEvent(USER_ID));
        listener.onInventoryChanged(new InventoryChangedEvent(USER_ID));
        listener.flushSettledInventories();
        listener.flushSettledInventories();

        verify(evaluationService, times(1)).evaluateForTrigger(USER_ID, MilestoneTrigger.ITEM);
    }

    @Test
    void inventoryChangeInsideQuietWindowIsHeldBack() {
        MilestoneTriggerListener listener = listener(3600, 7200);

        listener.onInventoryChanged(new InventoryChangedEvent(USER_ID));
        listener.flushSettledInventories();

        verify(evaluationService, never()).evaluateForTrigger(USER_ID, MilestoneTrigger.ITEM);
    }

    @Test
    void maxWaitFiresEvenWhileChangesKeepArriving() {
        MilestoneTriggerListener listener = listener(3600, 0);

        listener.onInventoryChanged(new InventoryChangedEvent(USER_ID));
        listener.onInventoryChanged(new InventoryChangedEvent(USER_ID));
        listener.flushSettledInventories();

        verify(evaluationService, times(1)).evaluateForTrigger(USER_ID, MilestoneTrigger.ITEM);
    }

    @Test
    void separateUsersDispatchIndependently() {
        MilestoneTriggerListener listener = listener(0, 0);

        listener.onInventoryChanged(new InventoryChangedEvent(USER_ID));
        listener.onInventoryChanged(new InventoryChangedEvent(OTHER_ID));
        listener.flushSettledInventories();

        verify(evaluationService).evaluateForTrigger(USER_ID, MilestoneTrigger.ITEM);
        verify(evaluationService).evaluateForTrigger(OTHER_ID, MilestoneTrigger.ITEM);
    }

    @Test
    void nullUserIsIgnored() {
        MilestoneTriggerListener listener = listener(0, 0);

        listener.onInventoryChanged(new InventoryChangedEvent(null));
        listener.flushSettledInventories();

        verify(evaluationService, never()).evaluateForTrigger(null, MilestoneTrigger.ITEM);
    }

    @Test
    void marketEventDispatchesBothWinnerAndSeller() {
        MilestoneTriggerListener listener = listener(0, 0);

        listener.onMarketListing(new MarketListingEvent(UUID.randomUUID(), "sold", MarketListingStatus.sold,
                100L, ref(USER_ID), ref(OTHER_ID), Instant.now()));

        verify(evaluationService).evaluateForTrigger(USER_ID, MilestoneTrigger.MARKET);
        verify(evaluationService).evaluateForTrigger(OTHER_ID, MilestoneTrigger.MARKET);
    }

    @Test
    void marketEventWithoutActorStillReachesSeller() {
        MilestoneTriggerListener listener = listener(0, 0);

        listener.onMarketListing(new MarketListingEvent(UUID.randomUUID(), "expired", MarketListingStatus.expired,
                null, null, ref(OTHER_ID), Instant.now()));

        verify(evaluationService, times(1)).evaluateForTrigger(OTHER_ID, MilestoneTrigger.MARKET);
    }

    @Test
    void campaignCompletionDispatchesPlayerAndCreator() {
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findCreatorIdByIdAndActiveTrue(campaignId)).thenReturn(Optional.of(OTHER_ID));
        MilestoneTriggerListener listener = listener(0, 0);

        listener.onCampaignCompleted(new CampaignCompletedEvent(USER_ID, campaignId, CampaignStatus.CURATED,
                Instant.now()));

        verify(evaluationService).evaluateForTrigger(USER_ID, MilestoneTrigger.CAMPAIGN);
        verify(evaluationService).evaluateForTrigger(OTHER_ID, MilestoneTrigger.CAMPAIGN);
    }

    @Test
    void campaignDistinctionDispatchesCreatorCollaboratorsAndCompletersOnce() {
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findCreatorIdByIdAndActiveTrue(campaignId)).thenReturn(Optional.of(USER_ID));
        when(campaignCollaboratorRepository.findUserIdsByCampaignAndStatus(campaignId,
                CampaignCollaboratorStatus.ACCEPTED)).thenReturn(List.of(OTHER_ID));
        when(userCampaignRepository.findUserIdsByCampaignAndStatuses(campaignId,
                List.of(UserCampaignStatus.COMPLETED))).thenReturn(List.of(USER_ID, THIRD_ID));
        MilestoneTriggerListener listener = listener(0, 0);

        listener.onCampaignDistinction(new CampaignDistinctionEvent(campaignId));

        verify(evaluationService, times(1)).evaluateForTrigger(USER_ID, MilestoneTrigger.CAMPAIGN);
        verify(evaluationService, times(1)).evaluateForTrigger(OTHER_ID, MilestoneTrigger.CAMPAIGN);
        verify(evaluationService, times(1)).evaluateForTrigger(THIRD_ID, MilestoneTrigger.CAMPAIGN);
    }

    @Test
    void creatorCompletingTheirOwnCampaignIsDispatchedOnce() {
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findCreatorIdByIdAndActiveTrue(campaignId)).thenReturn(Optional.of(USER_ID));
        MilestoneTriggerListener listener = listener(0, 0);

        listener.onCampaignCompleted(new CampaignCompletedEvent(USER_ID, campaignId, CampaignStatus.CURATED,
                Instant.now()));

        verify(evaluationService, times(1)).evaluateForTrigger(USER_ID, MilestoneTrigger.CAMPAIGN);
    }
}
