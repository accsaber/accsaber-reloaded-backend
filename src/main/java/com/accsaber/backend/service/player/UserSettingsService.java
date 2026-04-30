package com.accsaber.backend.service.player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.user.User;
import com.accsaber.backend.model.entity.user.UserSetting;
import com.accsaber.backend.model.entity.user.UserSettingKey;
import com.accsaber.backend.repository.user.UserRepository;
import com.accsaber.backend.repository.user.UserSettingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSettingsService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UserSettingRepository settingRepository;
    private final UserRepository userRepository;

    public <T> T get(Long userId, UserSettingKey key, Class<T> type) {
        if (!key.valueType().equals(type)) {
            throw new IllegalArgumentException("Type mismatch for setting " + key.key()
                    + ": expected " + key.valueType().getSimpleName() + ", got " + type.getSimpleName());
        }
        return settingRepository.findByUser_IdAndKey(userId, key.key())
                .map(s -> deserialize(s.getValue(), type))
                .orElseGet(() -> type.cast(key.defaultValue()));
    }

    public Map<String, Object> getAll(Long userId) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (UserSettingKey k : UserSettingKey.values()) {
            out.put(k.key(), serializeForResponse(k.defaultValue()));
        }
        for (UserSetting s : settingRepository.findByUser_Id(userId)) {
            out.put(s.getKey(), nodeToObject(s.getValue()));
        }
        return out;
    }

    public Map<String, Object> getGroup(Long userId, String group) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (UserSettingKey k : UserSettingKey.values()) {
            if (k.group().equals(group)) {
                out.put(k.key(), serializeForResponse(k.defaultValue()));
            }
        }
        for (UserSetting s : settingRepository.findByUser_IdAndKeyPrefix(userId, group + ".")) {
            out.put(s.getKey(), nodeToObject(s.getValue()));
        }
        return out;
    }

    public Map<String, Object> getPublicGroup(Long userId, String group) {
        Map<String, Object> all = getGroup(userId, group);
        Map<String, Object> filtered = new LinkedHashMap<>();
        all.forEach((key, value) -> UserSettingKey.byKey(key)
                .filter(UserSettingKey::publicReadable)
                .ifPresent(k -> filtered.put(key, value)));
        return filtered;
    }

    @Transactional
    public Map<String, Object> updateGroup(Long userId, String group, Map<String, Object> patch) {
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        for (Map.Entry<String, Object> entry : patch.entrySet()) {
            UserSettingKey key = UserSettingKey.byKey(entry.getKey())
                    .orElseThrow(() -> new ValidationException("Unknown setting: " + entry.getKey()));
            if (!key.group().equals(group)) {
                throw new ValidationException(
                        "Setting " + key.key() + " does not belong to group '" + group + "'");
            }
            JsonNode value = MAPPER.valueToTree(entry.getValue());
            validate(key, value);
            UserSetting existing = settingRepository.findByUser_IdAndKey(userId, key.key()).orElse(null);
            if (existing == null) {
                settingRepository.save(UserSetting.builder().user(user).key(key.key()).value(value).build());
            } else {
                existing.setValue(value);
                settingRepository.save(existing);
            }
        }
        return getGroup(userId, group);
    }

    private Object serializeForResponse(Object defaultValue) {
        if (defaultValue == null) return null;
        if (defaultValue instanceof Enum<?> e) {
            return MAPPER.convertValue(e, Object.class);
        }
        return defaultValue;
    }

    private Object nodeToObject(JsonNode node) {
        try {
            return MAPPER.treeToValue(node, Object.class);
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage(), ex);
        }
    }

    public List<String> knownGroups() {
        return java.util.Arrays.stream(UserSettingKey.values()).map(UserSettingKey::group).distinct().toList();
    }

    private void validate(UserSettingKey key, JsonNode value) {
        try {
            deserialize(value, key.valueType());
        } catch (RuntimeException ex) {
            throw new ValidationException(key.key(), "invalid value: " + ex.getMessage());
        }
    }

    private <T> T deserialize(JsonNode node, Class<T> type) {
        try {
            return MAPPER.treeToValue(node, type);
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage(), ex);
        }
    }
}
