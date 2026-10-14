package com.accsaber.backend.model.dto.request.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.ServletRequestDataBinder;

import com.accsaber.backend.model.entity.campaign.CampaignStatus;
import com.accsaber.backend.model.entity.campaign.UserCampaignStatus;

class CampaignFilterBindingTest {

    @Test
    void bindsEveryQueryParamByItsExistingName() {
        UUID tagId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("status", "PUBLISHED", "CURATED");
        request.addParameter("tagIds", tagId.toString());
        request.addParameter("creatorId", "5");
        request.addParameter("memberId", "6");
        request.addParameter("search", "jumps");
        request.addParameter("official", "true");
        request.addParameter("loved", "false");
        request.addParameter("progressStatus", "COMPLETED");

        CampaignFilter filter = bind(request);

        assertThat(filter.status()).containsExactly(CampaignStatus.PUBLISHED, CampaignStatus.CURATED);
        assertThat(filter.tagIds()).containsExactly(tagId);
        assertThat(filter.creatorId()).isEqualTo(5L);
        assertThat(filter.memberId()).isEqualTo(6L);
        assertThat(filter.search()).isEqualTo("jumps");
        assertThat(filter.official()).isTrue();
        assertThat(filter.loved()).isFalse();
        assertThat(filter.progressStatus()).containsExactly(UserCampaignStatus.COMPLETED);
    }

    @Test
    void splitsCommaSeparatedListsLikeRequestParams() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("status", "PUBLISHED,CURATED");

        assertThat(bind(request).status()).containsExactly(CampaignStatus.PUBLISHED, CampaignStatus.CURATED);
    }

    @Test
    void leavesAbsentParamsNull() {
        CampaignFilter filter = bind(new MockHttpServletRequest());

        assertThat(filter).isEqualTo(CampaignFilter.builder().build());
    }

    private static CampaignFilter bind(MockHttpServletRequest request) {
        ServletRequestDataBinder binder = new ServletRequestDataBinder(null, "filter");
        binder.setTargetType(ResolvableType.forClass(CampaignFilter.class));
        binder.setConversionService(new DefaultFormattingConversionService());
        binder.construct(request);
        assertThat(binder.getBindingResult().hasErrors()).isFalse();
        return (CampaignFilter) binder.getTarget();
    }
}
