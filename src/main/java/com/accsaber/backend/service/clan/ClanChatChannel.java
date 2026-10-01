package com.accsaber.backend.service.clan;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.accsaber.backend.model.dto.response.chat.ChatMessageResponse;
import com.accsaber.backend.model.dto.response.clan.PublicClanResponse;
import com.accsaber.backend.model.entity.chat.ChatMessage;
import com.accsaber.backend.model.entity.clan.Clan;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.event.ClanMembershipChangedEvent;
import com.accsaber.backend.repository.chat.ChatMessageRepository;
import com.accsaber.backend.repository.clan.ClanMemberRepository;
import com.accsaber.backend.repository.clan.ClanRepository;
import com.accsaber.backend.service.chat.ChatChannel;
import com.accsaber.backend.service.chat.ChatService;
import com.accsaber.backend.websocket.server.ClanChatWebSocketHandler;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ClanChatChannel implements ChatChannel {

    private final ChatMessageRepository chatRepository;
    private final ClanRepository clanRepository;
    private final ClanMemberRepository memberRepository;
    private final ClanAccessService accessService;
    private final ClanCosmeticService cosmeticService;
    private final ChatService chatService;
    private final ClanChatWebSocketHandler chatHandler;

    public void announce(Clan clan, ChatNotice notice) {
        chatService.post(this, clan.getId(), ChatMessage.builder()
                .clan(clan)
                .user(notice.actor())
                .event(notice.event())
                .subjectUser(notice.subjectUser())
                .subjectClan(notice.subjectClan())
                .war(notice.war())
                .build());
    }

    @Override
    public void assertParticipant(UUID clanId, Long userId) {
        accessService.require(clanId, userId, ClanPermission.CHAT);
    }

    @Override
    public Page<ChatMessage> findMessages(UUID clanId, Pageable pageable) {
        return chatRepository.findByClan_IdOrderByCreatedAtDesc(clanId, pageable);
    }

    @Override
    public ChatMessage newMessage(UUID clanId, User author, String content) {
        return ChatMessage.builder()
                .clan(clanRepository.getReferenceById(clanId))
                .user(author)
                .content(content)
                .build();
    }

    @Override
    public List<ChatMessageResponse> toResponses(List<ChatMessage> messages) {
        Map<UUID, PublicClanResponse> refs = cosmeticService.publicRefs(messages.stream()
                .map(ChatMessage::getSubjectClan)
                .filter(Objects::nonNull)
                .toList());
        return messages.stream()
                .map(message -> ChatMessageResponse.of(message, message.getSubjectClan() == null ? null
                        : refs.get(message.getSubjectClan().getId())))
                .toList();
    }

    @Override
    public void broadcast(UUID clanId, String json) {
        chatHandler.broadcast(clanId, json);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMembershipChanged(ClanMembershipChangedEvent event) {
        chatHandler.keepOnly(event.clanId(), () -> memberRepository.findOpenUserIds(event.clanId()));
    }
}
