package com.accsaber.backend.model.dto.response.common;

import com.accsaber.backend.model.entity.user.User;

public record PlayerRef(String id, String name, String avatarUrl, String cdnAvatarUrl, String country) {

    public static PlayerRef of(User user) {
        if (user == null) {
            return null;
        }
        return new PlayerRef(String.valueOf(user.getId()), user.getName(), user.getAvatarUrl(),
                user.getCdnAvatarUrl(), user.getCountry());
    }
}
