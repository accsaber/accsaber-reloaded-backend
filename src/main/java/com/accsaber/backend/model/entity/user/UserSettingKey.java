package com.accsaber.backend.model.entity.user;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public enum UserSettingKey {

    PRIVACY_FOLLOWING_VISIBILITY("privacy.followingVisibility", Visibility.class, Visibility.PUBLIC, true),
    PRIVACY_RIVALS_VISIBILITY("privacy.rivalsVisibility", Visibility.class, Visibility.PUBLIC, true),

    APPEARANCE_THEME("appearance.theme", String.class, "system", false),
    APPEARANCE_COLOR_SCHEME("appearance.colorScheme", String.class, "default", false);

    public static final String GROUP_PRIVACY = "privacy";
    public static final String GROUP_APPEARANCE = "appearance";

    private final String key;
    private final Class<?> valueType;
    private final Object defaultValue;
    private final boolean publicReadable;

    UserSettingKey(String key, Class<?> valueType, Object defaultValue, boolean publicReadable) {
        this.key = key;
        this.valueType = valueType;
        this.defaultValue = defaultValue;
        this.publicReadable = publicReadable;
    }

    public String key() {
        return key;
    }

    public Class<?> valueType() {
        return valueType;
    }

    public Object defaultValue() {
        return defaultValue;
    }

    public boolean publicReadable() {
        return publicReadable;
    }

    public String group() {
        int dot = key.indexOf('.');
        return dot < 0 ? key : key.substring(0, dot);
    }

    private static final Map<String, UserSettingKey> BY_KEY = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(UserSettingKey::key, k -> k));

    public static Optional<UserSettingKey> byKey(String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }
}
