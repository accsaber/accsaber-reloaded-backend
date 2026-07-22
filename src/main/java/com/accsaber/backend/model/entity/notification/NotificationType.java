package com.accsaber.backend.model.entity.notification;

import com.accsaber.backend.model.entity.user.UserSettingKey;

public enum NotificationType {

    trade_offer(UserSettingKey.NOTIFICATIONS_TRADE_OFFER),
    trade_accepted(UserSettingKey.NOTIFICATIONS_TRADE_RESOLVED),
    trade_declined(UserSettingKey.NOTIFICATIONS_TRADE_RESOLVED),
    market_sold(UserSettingKey.NOTIFICATIONS_MARKET_SOLD),
    market_bid(UserSettingKey.NOTIFICATIONS_MARKET_BID),
    market_outbid(UserSettingKey.NOTIFICATIONS_MARKET_OUTBID),
    market_won(UserSettingKey.NOTIFICATIONS_MARKET_WON),
    item_earned(UserSettingKey.NOTIFICATIONS_ITEM_EARNED),
    server(UserSettingKey.NOTIFICATIONS_SERVER);

    private final UserSettingKey preference;

    NotificationType(UserSettingKey preference) {
        this.preference = preference;
    }

    public UserSettingKey preference() {
        return preference;
    }
}
