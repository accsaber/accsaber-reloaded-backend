package com.accsaber.backend.service.item;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.config.ItemFilesProperties;
import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.item.UserItemLink;
import com.accsaber.backend.repository.item.UserItemLinkRepository;
import com.accsaber.backend.service.player.DuplicateUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ItemFileService {

    public static final byte[] TRAILER_MAGIC = "ACCSIGv1".getBytes(StandardCharsets.US_ASCII);
    private static final int SIGNATURE_LENGTH = 64;
    private static final byte[] ED25519_PKCS8_PREFIX = HexFormat.of()
            .parseHex("302e020100300506032b657004220420");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ItemFilesProperties properties;
    private final UserItemLinkRepository userItemLinkRepository;
    private final DuplicateUserService duplicateUserService;

    private volatile PrivateKey signingKey;

    public record SignedItemFile(String fileName, byte[] bytes) {
    }

    @Transactional(readOnly = true)
    public SignedItemFile download(Long userId, UUID linkId) {
        Long resolved = duplicateUserService.resolvePrimaryUserId(userId);
        UserItemLink link = userItemLinkRepository.findById(linkId)
                .orElseThrow(() -> new ResourceNotFoundException("UserItemLink", linkId));
        if (!link.getUser().getId().equals(resolved)) {
            throw new ValidationException("linkId", "user does not own this item link");
        }
        Item item = link.getItem();
        if (!item.isActive() || !item.isDownloadable()) {
            throw new ValidationException("linkId", "this item is not downloadable");
        }
        Path file = resolveFile(item);
        byte[] bundle = readFile(file);
        byte[] payload = buildPayload(item, file.getFileName().toString(), resolved, linkId, bundle);
        return new SignedItemFile(file.getFileName().toString(), assemble(bundle, payload, sign(payload)));
    }

    private Path resolveFile(Item item) {
        String relative = item.getValue() != null ? item.getValue().path("file").asText(null) : null;
        if (relative == null || relative.isBlank()) {
            throw new ConflictException("item '" + item.getName() + "' has no file configured");
        }
        Path base = Path.of(properties.getStoragePath()).toAbsolutePath().normalize();
        Path target = base.resolve(relative).normalize();
        if (!target.startsWith(base)) {
            throw new ValidationException("file", "invalid item file path");
        }
        if (!Files.isRegularFile(target)) {
            throw new ResourceNotFoundException("ItemFile", relative);
        }
        return target;
    }

    private byte[] readFile(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read item file " + file.getFileName(), e);
        }
    }

    private byte[] buildPayload(Item item, String fileName, Long userId, UUID linkId, byte[] bundle) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("v", 1);
        payload.put("item", item.getId().toString());
        payload.put("name", item.getName());
        payload.put("file", fileName);
        payload.put("user", userId);
        payload.put("link", linkId.toString());
        payload.put("issued", Instant.now().toString());
        payload.put("sha256", HexFormat.of().formatHex(sha256(bundle)));
        return payload.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] assemble(byte[] bundle, byte[] payload, byte[] signature) {
        ByteBuffer out = ByteBuffer
                .allocate(bundle.length + TRAILER_MAGIC.length * 2 + payload.length + signature.length + 4)
                .order(ByteOrder.LITTLE_ENDIAN);
        out.put(bundle).put(TRAILER_MAGIC).put(payload).put(signature).putInt(payload.length).put(TRAILER_MAGIC);
        return out.array();
    }

    private byte[] sign(byte[] payload) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(loadSigningKey());
            signature.update(payload);
            byte[] sig = signature.sign();
            if (sig.length != SIGNATURE_LENGTH) {
                throw new IllegalStateException("Unexpected Ed25519 signature length " + sig.length);
            }
            return sig;
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign item file", e);
        }
    }

    private PrivateKey loadSigningKey() throws java.security.GeneralSecurityException {
        PrivateKey key = signingKey;
        if (key != null) {
            return key;
        }
        if (properties.getSigningKey() == null || properties.getSigningKey().isBlank()) {
            throw new IllegalStateException("ITEM_FILES_SIGNING_KEY is not configured");
        }
        byte[] encoded = Base64.getDecoder().decode(properties.getSigningKey());
        if (encoded.length == 32) {
            encoded = ByteBuffer.allocate(ED25519_PKCS8_PREFIX.length + 32)
                    .put(ED25519_PKCS8_PREFIX).put(encoded).array();
        }
        key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        signingKey = key;
        return key;
    }

    private byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
