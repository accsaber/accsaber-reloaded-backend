package com.accsaber.backend.model.entity.user;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public enum UserSettingKey {

    PRIVACY_FOLLOWING_VISIBILITY("privacy.followingVisibility", Visibility.class, Visibility.PUBLIC, true),
    PRIVACY_RIVALS_VISIBILITY("privacy.rivalsVisibility", Visibility.class, Visibility.PUBLIC, true),

    APPEARANCE_THEME("appearance.theme", String.class, "system", false),
    APPEARANCE_COLOR_SCHEME("appearance.colorScheme", String.class, "default", false),
    APPEARANCE_PRIMARY_REPLAY_SERVICE("appearance.primaryReplayService", String.class, "beatleader", false),

    SYNC_NAME("sync.name", Boolean.class, true, false),
    SYNC_AVATAR("sync.avatar", Boolean.class, true, false),

    EQUIPPED_TITLE("equipped.title", UUID.class, null, true),
    EQUIPPED_PROFILE_BORDER_SHAPE("equipped.profileBorderShape", UUID.class, null, true),
    EQUIPPED_PROFILE_BORDER_COLOR("equipped.profileBorderColor", UUID.class, null, true),
    EQUIPPED_THEME("equipped.theme", UUID.class, null, true),
    EQUIPPED_PROFILE_BACKGROUND("equipped.profileBackground", UUID.class, null, true),
    EQUIPPED_PROFILE_THUMBNAIL_BACKGROUND("equipped.profileThumbnailBackground", UUID.class, null, true),
    EQUIPPED_STATISTIC("equipped.statistic", UUID.class, null, true),

    EQUIPPED_TITLE_VARIANT("equipped.titleVariant", String.class, null, true),
    EQUIPPED_PROFILE_BORDER_SHAPE_VARIANT("equipped.profileBorderShapeVariant", String.class, null, true),
    EQUIPPED_PROFILE_BORDER_COLOR_VARIANT("equipped.profileBorderColorVariant", String.class, null, true),
    EQUIPPED_THEME_VARIANT("equipped.themeVariant", String.class, null, true),
    EQUIPPED_PROFILE_BACKGROUND_VARIANT("equipped.profileBackgroundVariant", String.class, null, true),
    EQUIPPED_PROFILE_THUMBNAIL_BACKGROUND_VARIANT("equipped.profileThumbnailBackgroundVariant", String.class, null, true),
    EQUIPPED_STATISTIC_VARIANT("equipped.statisticVariant", String.class, null, true);

    public static final String GROUP_PRIVACY = "privacy";
    public static final String GROUP_APPEARANCE = "appearance";
    public static final String GROUP_EQUIPPED = "equipped";
    public static final String GROUP_SYNC = "sync";

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

    public static Optional<UserSettingKey> forEquippedItemType(String typeKey) {
        return Optional.ofNullable(EQUIP_BY_TYPE_KEY.get(typeKey));
    }

    public static Optional<UserSettingKey> forEquippedItemVariant(String typeKey) {
        return Optional.ofNullable(EQUIP_VARIANT_BY_TYPE_KEY.get(typeKey));
    }

    public Optional<String> equippedTypeKey() {
        return Optional.ofNullable(TYPE_KEY_BY_EQUIP.get(this));
    }

    private static final Map<String, UserSettingKey> EQUIP_BY_TYPE_KEY = Map.of(
            "title", EQUIPPED_TITLE,
            "profile_border_shape", EQUIPPED_PROFILE_BORDER_SHAPE,
            "profile_border_color", EQUIPPED_PROFILE_BORDER_COLOR,
            "theme", EQUIPPED_THEME,
            "profile_background", EQUIPPED_PROFILE_BACKGROUND,
            "profile_thumbnail_background", EQUIPPED_PROFILE_THUMBNAIL_BACKGROUND,
            "statistic", EQUIPPED_STATISTIC);

    private static final Map<String, UserSettingKey> EQUIP_VARIANT_BY_TYPE_KEY = Map.of(
            "title", EQUIPPED_TITLE_VARIANT,
            "profile_border_shape", EQUIPPED_PROFILE_BORDER_SHAPE_VARIANT,
            "profile_border_color", EQUIPPED_PROFILE_BORDER_COLOR_VARIANT,
            "theme", EQUIPPED_THEME_VARIANT,
            "profile_background", EQUIPPED_PROFILE_BACKGROUND_VARIANT,
            "profile_thumbnail_background", EQUIPPED_PROFILE_THUMBNAIL_BACKGROUND_VARIANT,
            "statistic", EQUIPPED_STATISTIC_VARIANT);

    private static final Map<UserSettingKey, String> TYPE_KEY_BY_EQUIP = EQUIP_BY_TYPE_KEY.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));
}
