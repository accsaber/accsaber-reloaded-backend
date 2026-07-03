package com.accsaber.backend.service.item;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.dto.response.milestone.LevelResponse;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.milestone.LevelThresholdRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.milestone.LevelService;

@ExtendWith(MockitoExtension.class)
class LevelUpAwardServiceTest {

    @Mock
    private LevelService levelService;
    @Mock
    private LevelThresholdRepository levelThresholdRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private ItemService itemService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private LevelUpAwardService service;

    @Test
    void addCampaignXpIncrementsCampaignBucketAndTotal() {
        when(userRepository.findTotalXpById(50L)).thenReturn(Optional.of(BigDecimal.ZERO));
        when(levelService.calculateLevel(any())).thenReturn(LevelResponse.builder().level(0).build());

        service.addCampaignXp(50L, new BigDecimal("100"));

        verify(userRepository).addCampaignXp(50L, new BigDecimal("100"));
        verify(userRepository).addXp(50L, new BigDecimal("100"));
    }

    @Test
    void addCampaignXpIgnoresNonPositiveDelta() {
        service.addCampaignXp(50L, BigDecimal.ZERO);

        verify(userRepository, never()).addCampaignXp(any(), any());
        verify(userRepository, never()).addXp(any(), any());
    }
}
