package com.accsaber.backend.model.dto.response.market;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MarketUserRef {

    private Long id;
    private String name;
    private String avatarUrl;
    private String cdnAvatarUrl;
    private String country;
}
