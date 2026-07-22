package com.accsaber.backend.service.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.entity.notification.Notification;
import com.accsaber.backend.model.entity.notification.NotificationType;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserSettingKey;
import com.accsaber.backend.model.event.NotificationCreatedEvent;
import com.accsaber.backend.repository.notification.NotificationRepository;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.accsaber.backend.service.player.UserSettingsService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    private static final Long RECIPIENT = 1L;
    private static final Long ACTOR = 2L;

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSettingsService userSettingsService;
    @Mock
    private DuplicateUserService duplicateUserService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        when(duplicateUserService.resolvePrimaryUserId(RECIPIENT)).thenReturn(RECIPIENT);
        when(duplicateUserService.resolvePrimaryUserId(ACTOR)).thenReturn(ACTOR);
        when(userRepository.getReferenceById(any())).thenAnswer(inv -> User.builder()
                .id(inv.getArgument(0)).name("player").build());
        when(notificationRepository.save(any())).thenAnswer(returnsFirstArg());
        enable(true);
    }

    @Test
    void aNotificationIsStoredAndPushed() {
        notificationService.notify(RECIPIENT, NotificationType.trade_offer, ACTOR,
                "You received a new trade offer", "/trades/abc");

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(NotificationType.trade_offer);
        assertThat(saved.getValue().getUser().getId()).isEqualTo(RECIPIENT);
        assertThat(saved.getValue().getTitle()).isEqualTo("You received a new trade offer");
        assertThat(saved.getValue().getLinkTo()).isEqualTo("/trades/abc");
        verify(eventPublisher).publishEvent(any(NotificationCreatedEvent.class));
    }

    @Test
    void aDisabledPreferenceSuppressesTheNotificationEntirely() {
        enable(false);

        notificationService.notify(RECIPIENT, NotificationType.trade_offer, ACTOR, "title", "/x");

        verify(notificationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(NotificationCreatedEvent.class));
    }

    @Test
    void anUnsetPreferenceDefaultsToEnabled() {
        when(userSettingsService.get(RECIPIENT, UserSettingKey.NOTIFICATIONS_TRADE_OFFER, Boolean.class))
                .thenReturn(null);

        notificationService.notify(RECIPIENT, NotificationType.trade_offer, ACTOR, "title", "/x");

        verify(notificationRepository).save(any());
    }

    @Test
    void aUserIsNeverNotifiedAboutTheirOwnAction() {
        notificationService.notify(RECIPIENT, NotificationType.market_bid, RECIPIENT, "title", "/x");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void selfNotificationIsBlockedEvenThroughADuplicateAccount() {
        when(duplicateUserService.resolvePrimaryUserId(ACTOR)).thenReturn(RECIPIENT);

        notificationService.notify(RECIPIENT, NotificationType.market_bid, ACTOR, "title", "/x");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void aNotificationWithNoActorIsStillDelivered() {
        notificationService.notify(RECIPIENT, NotificationType.item_earned, null,
                "You received Fiery Title!", "/inventory?highlight=abc");

        verify(notificationRepository).save(any());
    }

    @Test
    void aNullRecipientIsIgnoredRatherThanThrowing() {
        notificationService.notify(null, NotificationType.trade_offer, ACTOR, "title", "/x");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void aBroadcastDelegatesToTheSetBasedInsert() {
        when(notificationRepository.broadcast("Week 2 is live", "/events/summer")).thenReturn(1234);

        assertThat(notificationService.broadcast("Week 2 is live", "/events/summer")).isEqualTo(1234);
    }

    @Test
    void testFireDeliversASampleTitleAndLinkForTheType() {
        when(userRepository.findByIdAndActiveTrue(RECIPIENT))
                .thenReturn(Optional.of(User.builder().id(RECIPIENT).name("tester").build()));

        NotificationService.TestFireResult res = notificationService.testFire(
                RECIPIENT, NotificationType.item_earned, null, null);

        assertThat(res.delivered()).isTrue();
        assertThat(res.userName()).isEqualTo("tester");
        assertThat(res.title()).isEqualTo("You received Alpha Crate!");
        assertThat(res.linkTo()).isEqualTo("/players/" + RECIPIENT);
        assertThat(res.suppressedReason()).isNull();
        verify(notificationRepository).save(any());
    }

    @Test
    void testFireHonoursAnExplicitTitleAndLink() {
        when(userRepository.findByIdAndActiveTrue(RECIPIENT))
                .thenReturn(Optional.of(User.builder().id(RECIPIENT).name("tester").build()));

        NotificationService.TestFireResult res = notificationService.testFire(
                RECIPIENT, NotificationType.server, "Custom copy", "/events/x");

        assertThat(res.title()).isEqualTo("Custom copy");
        assertThat(res.linkTo()).isEqualTo("/events/x");
    }

    @Test
    void testFireReportsWhyNothingArrivedWhenThePlayerDisabledTheType() {
        when(userRepository.findByIdAndActiveTrue(RECIPIENT))
                .thenReturn(Optional.of(User.builder().id(RECIPIENT).name("tester").build()));
        enable(false);

        NotificationService.TestFireResult res = notificationService.testFire(
                RECIPIENT, NotificationType.market_bid, null, null);

        assertThat(res.delivered()).isFalse();
        assertThat(res.suppressedReason()).contains("notifications.marketBid");
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void testFireAgainstAnUnknownPlayerIsRejected() {
        when(userRepository.findByIdAndActiveTrue(RECIPIENT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.testFire(
                RECIPIENT, NotificationType.trade_offer, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void everyTypeHasASampleTitleSoTestFireNeverSendsAnEmptyNotification() {
        when(userRepository.findByIdAndActiveTrue(RECIPIENT))
                .thenReturn(Optional.of(User.builder().id(RECIPIENT).name("tester").build()));

        for (NotificationType type : NotificationType.values()) {
            NotificationService.TestFireResult res = notificationService.testFire(
                    RECIPIENT, type, null, null);
            assertThat(res.title()).as("sample title for %s", type).isNotBlank();
        }
    }

    @Test
    void bothTradeResolutionTypesShareOnePreference() {
        assertThat(NotificationType.trade_accepted.preference())
                .isEqualTo(NotificationType.trade_declined.preference())
                .isEqualTo(UserSettingKey.NOTIFICATIONS_TRADE_RESOLVED);
    }

    @Test
    void everyNotificationTypeHasAPreferenceInTheNotificationsGroup() {
        for (NotificationType type : NotificationType.values()) {
            assertThat(type.preference()).as("preference for %s", type).isNotNull();
            assertThat(type.preference().group()).isEqualTo(UserSettingKey.GROUP_NOTIFICATIONS);
            assertThat(type.preference().valueType()).isEqualTo(Boolean.class);
            assertThat(type.preference().defaultValue()).isEqualTo(true);
        }
    }

    private void enable(boolean enabled) {
        when(userSettingsService.get(any(), any(UserSettingKey.class), any()))
                .thenReturn(enabled);
    }
}
