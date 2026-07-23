package com.accsaber.backend.service.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.config.ItemFilesProperties;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ItemFileServiceTest {

    private static final Long USER_ID = 76561198087536397L;
    private static final UUID LINK_ID = UUID.randomUUID();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private UserItemLinkRepository userItemLinkRepository;
    @Mock
    private DuplicateUserService duplicateUserService;

    @TempDir
    private Path storageDir;

    private ItemFilesProperties properties;
    private ItemFileService itemFileService;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        properties = new ItemFilesProperties();
        properties.setStoragePath(storageDir.toString());
        properties.setSigningKey(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        itemFileService = new ItemFileService(properties, userItemLinkRepository, duplicateUserService);
    }

    @Test
    void downloadAppendsVerifiableSignedTrailer() throws Exception {
        byte[] bundle = "UnityFS-fake-bundle-content".getBytes(StandardCharsets.UTF_8);
        Files.write(storageDir.resolve("god_saber.saber"), bundle);
        stubOwnedLink(item("God Saber", "god_saber.saber", true));

        var result = itemFileService.download(USER_ID, LINK_ID);

        assertThat(result.fileName()).isEqualTo("god_saber.saber");
        byte[] out = result.bytes();
        byte[] magic = ItemFileService.TRAILER_MAGIC;
        assertThat(Arrays.copyOfRange(out, out.length - magic.length, out.length)).isEqualTo(magic);

        int payloadLength = ByteBuffer.wrap(out, out.length - magic.length - 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN).getInt();
        int sigStart = out.length - magic.length - 4 - 64;
        int payloadStart = sigStart - payloadLength;
        byte[] payload = Arrays.copyOfRange(out, payloadStart, sigStart);
        byte[] sig = Arrays.copyOfRange(out, sigStart, sigStart + 64);
        assertThat(Arrays.copyOfRange(out, payloadStart - magic.length, payloadStart)).isEqualTo(magic);
        assertThat(Arrays.copyOfRange(out, 0, payloadStart - magic.length)).isEqualTo(bundle);

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(keyPair.getPublic());
        verifier.update(payload);
        assertThat(verifier.verify(sig)).isTrue();

        JsonNode parsed = MAPPER.readTree(payload);
        assertThat(parsed.get("user").asLong()).isEqualTo(USER_ID);
        assertThat(parsed.get("link").asText()).isEqualTo(LINK_ID.toString());
        assertThat(parsed.get("file").asText()).isEqualTo("god_saber.saber");
        assertThat(parsed.get("sha256").asText())
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bundle)));
    }

    @Test
    void downloadRejectsLinkOwnedByAnotherUser() {
        UserItemLink link = link(item("God Saber", "god_saber.saber", true));
        link.getUser().setId(1234L);
        when(duplicateUserService.resolvePrimaryUserId(USER_ID)).thenReturn(USER_ID);
        when(userItemLinkRepository.findById(LINK_ID)).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> itemFileService.download(USER_ID, LINK_ID))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void downloadRejectsNonDownloadableItem() {
        stubOwnedLink(item("God Saber", "god_saber.saber", false));

        assertThatThrownBy(() -> itemFileService.download(USER_ID, LINK_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not downloadable");
    }

    @Test
    void downloadRejectsPathTraversal() {
        stubOwnedLink(item("God Saber", "../secrets.txt", true));

        assertThatThrownBy(() -> itemFileService.download(USER_ID, LINK_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("invalid item file path");
    }

    @Test
    void downloadRejectsItemWithoutFileValue() {
        Item item = item("God Saber", "unused", true);
        item.setValue(MAPPER.createObjectNode());
        stubOwnedLink(item);

        assertThatThrownBy(() -> itemFileService.download(USER_ID, LINK_ID))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void downloadRejectsMissingFileOnDisk() {
        stubOwnedLink(item("God Saber", "missing.saber", true));

        assertThatThrownBy(() -> itemFileService.download(USER_ID, LINK_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private void stubOwnedLink(Item item) {
        when(duplicateUserService.resolvePrimaryUserId(USER_ID)).thenReturn(USER_ID);
        when(userItemLinkRepository.findById(LINK_ID)).thenReturn(Optional.of(link(item)));
    }

    private Item item(String name, String file, boolean downloadable) {
        return Item.builder()
                .id(UUID.randomUUID())
                .name(name)
                .value(MAPPER.createObjectNode().put("file", file))
                .downloadable(downloadable)
                .active(true)
                .build();
    }

    private UserItemLink link(Item item) {
        User user = new User();
        user.setId(USER_ID);
        return UserItemLink.builder()
                .id(LINK_ID)
                .user(user)
                .item(item)
                .build();
    }
}
