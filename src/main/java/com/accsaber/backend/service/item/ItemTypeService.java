package com.accsaber.backend.service.item;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.entity.item.ItemType;
import com.accsaber.backend.repository.item.ItemTypeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemTypeService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ItemTypeRepository itemTypeRepository;

    public List<ItemType> findAllActive() {
        return itemTypeRepository.findByActiveTrue();
    }

    public List<ItemType> findAll() {
        return itemTypeRepository.findAll();
    }

    public ItemType findByIdActive(UUID id) {
        return itemTypeRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("ItemType", id));
    }

    @Transactional
    public ItemType create(UUID parentTypeId, String key, String name, String description, Object valueSchema) {
        if (itemTypeRepository.existsByKey(key)) {
            throw new ValidationException("key", "item type with key '" + key + "' already exists");
        }
        ItemType parent = parentTypeId == null ? null : findByIdActive(parentTypeId);
        ItemType type = ItemType.builder()
                .parentType(parent)
                .key(key)
                .name(name)
                .description(description)
                .valueSchema(toJsonNode(valueSchema))
                .build();
        return itemTypeRepository.save(type);
    }

    @Transactional
    public ItemType update(UUID id, String name, String description, Object valueSchema) {
        ItemType type = itemTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ItemType", id));
        if (name != null)
            type.setName(name);
        if (description != null)
            type.setDescription(description);
        if (valueSchema != null)
            type.setValueSchema(toJsonNode(valueSchema));
        return itemTypeRepository.save(type);
    }

    @Transactional
    public void deactivate(UUID id) {
        ItemType type = itemTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ItemType", id));
        type.setActive(false);
        itemTypeRepository.save(type);
    }

    @Transactional
    public ItemType reactivate(UUID id) {
        ItemType type = itemTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ItemType", id));
        type.setActive(true);
        return itemTypeRepository.save(type);
    }

    private JsonNode toJsonNode(Object value) {
        if (value == null)
            return null;
        return MAPPER.valueToTree(value);
    }
}
