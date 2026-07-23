package com.accsaber.backend.service.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.repository.item.ItemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ItemServiceUploadedImageTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String URL = "https://cdn.example.com/cdn/items/" + ID + ".webp?v=abc";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private ItemService itemService;

    @Test
    void uploadUpdatesBadgeRenderRasterAndIcon() throws Exception {
        JsonNode value = MAPPER.readTree(
                "{\"asset\":{\"raster\":{\"1x\":\"old.webp\"},\"altText\":\"ACC Champ\"}}");
        Item badge = Item.builder().id(ID).name("ACC Champ").value(value).build();
        when(itemRepository.findById(ID)).thenReturn(Optional.of(badge));
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));

        Item result = itemService.setUploadedImage(ID, URL);

        assertThat(result.getIconUrl()).isEqualTo(URL);
        assertThat(result.getValue().path("asset").path("raster").path("1x").asText()).isEqualTo(URL);
        assertThat(result.getValue().path("asset").path("altText").asText()).isEqualTo("ACC Champ");
    }

    @Test
    void uploadCreatesRasterWhenSeededImageless() throws Exception {
        JsonNode value = MAPPER.readTree("{\"asset\":{\"altText\":\"ACC Mercenary\"}}");
        Item badge = Item.builder().id(ID).name("ACC Mercenary").value(value).build();
        when(itemRepository.findById(ID)).thenReturn(Optional.of(badge));
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));

        Item result = itemService.setUploadedImage(ID, URL);

        assertThat(result.getValue().path("asset").path("raster").path("1x").asText()).isEqualTo(URL);
        assertThat(result.getValue().path("asset").path("altText").asText()).isEqualTo("ACC Mercenary");
    }

    @Test
    void uploadLeavesNonRenderItemValueUntouched() throws Exception {
        JsonNode value = MAPPER.readTree("{\"text\":\"Alive\"}");
        Item title = Item.builder().id(ID).name("Alive").value(value).build();
        when(itemRepository.findById(ID)).thenReturn(Optional.of(title));
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));

        Item result = itemService.setUploadedImage(ID, URL);

        assertThat(result.getIconUrl()).isEqualTo(URL);
        assertThat(result.getValue()).isEqualTo(value);
    }
}
