package com.accsaber.backend.model.event;

import com.accsaber.backend.model.entity.user.UserSettingKey;

public record UserSettingChangedEvent(Long userId, UserSettingKey key) {
}
