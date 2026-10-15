package com.accsaber.backend.service.mission;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.mission.EventRequest;
import com.accsaber.backend.model.entity.item.Item;
import com.accsaber.backend.model.entity.mission.Event;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.mission.EventRepository;
import com.accsaber.backend.service.media.MediaFormat;
import com.accsaber.backend.service.media.MediaProcessingService;
import com.accsaber.backend.util.Slugs;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventService {

    private static final String BACKGROUND_SUBDIR = "events";
    private static final String ICON_SUBDIR = "event-icons";

    private final EventRepository eventRepository;
    private final ItemRepository itemRepository;
    private final MediaProcessingService mediaProcessingService;
    private final TransactionTemplate transactionTemplate;

    public List<Event> listAdmin() {
        return eventRepository.findAllByOrderByStartsAtDesc();
    }

    public Optional<Event> findCurrent() {
        return eventRepository.findLive(Instant.now()).stream().findFirst();
    }

    public List<Event> listPublic(String state) {
        Instant now = Instant.now();
        if (state == null) {
            return eventRepository.findByActiveTrueOrderByStartsAtDesc();
        }
        return switch (state.toLowerCase()) {
            case "live" -> eventRepository.findLive(now);
            case "upcoming" -> eventRepository.findUpcoming(now);
            case "past" -> eventRepository.findPast(now);
            default -> throw new ValidationException("state", "must be live, upcoming or past");
        };
    }

    public Event findById(UUID id) {
        return eventRepository.findWithBonusItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event", id));
    }

    public UUID resolveId(String idOrSlug) {
        UUID id = Slugs.uuidOrNull(idOrSlug);
        if (id != null) {
            return id;
        }
        return eventRepository.findBySlug(idOrSlug)
                .map(Event::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", idOrSlug));
    }

    @Transactional
    public Event create(EventRequest req) {
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new ValidationException("title", "required");
        }
        if (req.getStartsAt() == null || req.getEndsAt() == null) {
            throw new ValidationException("startsAt/endsAt", "required");
        }
        validateWindow(req.getStartsAt(), req.getEndsAt());
        Event.EventBuilder builder = Event.builder()
                .title(req.getTitle())
                .slug(resolveSlug(req.getSlug(), req.getTitle(), null))
                .description(req.getDescription())
                .backgroundUrl(req.getBackgroundUrl())
                .iconUrl(req.getIconUrl())
                .startsAt(req.getStartsAt())
                .endsAt(req.getEndsAt())
                .active(req.getActive() == null || req.getActive());
        if (req.getBonusXp() != null) {
            builder.bonusXp(req.getBonusXp());
        }
        if (req.getBonusItemIds() != null) {
            builder.bonusItems(resolveBonusItems(req.getBonusItemIds()));
        }
        return eventRepository.save(builder.build());
    }

    @Transactional
    public Event update(UUID id, EventRequest req) {
        Event event = findById(id);
        if (req.getTitle() != null) {
            event.setTitle(req.getTitle());
        }
        if (req.getSlug() != null) {
            event.setSlug(resolveSlug(req.getSlug(), event.getTitle(), id));
        }
        if (req.getDescription() != null) {
            event.setDescription(req.getDescription());
        }
        if (req.getBackgroundUrl() != null) {
            event.setBackgroundUrl(req.getBackgroundUrl());
        }
        if (req.getIconUrl() != null) {
            event.setIconUrl(req.getIconUrl());
        }
        if (req.getStartsAt() != null) {
            event.setStartsAt(req.getStartsAt());
        }
        if (req.getEndsAt() != null) {
            event.setEndsAt(req.getEndsAt());
        }
        if (req.getBonusXp() != null) {
            event.setBonusXp(req.getBonusXp());
        }
        if (req.getBonusItemIds() != null) {
            event.setBonusItems(resolveBonusItems(req.getBonusItemIds()));
        }
        if (req.getActive() != null) {
            event.setActive(req.getActive());
        }
        validateWindow(event.getStartsAt(), event.getEndsAt());
        return eventRepository.save(event);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Event uploadBackground(UUID id, MultipartFile file) {
        findById(id);
        String url = mediaProcessingService.storeImage(file, BACKGROUND_SUBDIR, id.toString());
        return transactionTemplate.execute(status -> applyMedia(id, url, Event::setBackgroundUrl));
    }

    @Transactional
    public Event removeBackground(UUID id) {
        mediaProcessingService.deleteIfExists(BACKGROUND_SUBDIR, id.toString());
        return applyMedia(id, null, Event::setBackgroundUrl);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Event uploadIcon(UUID id, MultipartFile file) {
        findById(id);
        String url = mediaProcessingService.storeImage(file, ICON_SUBDIR, id.toString(), MediaFormat.GIF);
        return transactionTemplate.execute(status -> applyMedia(id, url, Event::setIconUrl));
    }

    @Transactional
    public Event removeIcon(UUID id) {
        mediaProcessingService.deleteIfExists(ICON_SUBDIR, id.toString());
        return applyMedia(id, null, Event::setIconUrl);
    }

    private Event applyMedia(UUID id, String url, BiConsumer<Event, String> setter) {
        Event event = findById(id);
        setter.accept(event, url);
        return eventRepository.save(event);
    }

    private List<Item> resolveBonusItems(List<UUID> ids) {
        List<UUID> distinct = List.copyOf(new HashSet<>(ids));
        List<Item> items = itemRepository.findAllById(distinct);
        if (items.size() != distinct.size()) {
            throw new ValidationException("bonusItemIds", "contains unknown item ids");
        }
        return items;
    }

    @Transactional
    public void delete(UUID id) {
        Event event = findById(id);
        event.setActive(false);
        eventRepository.save(event);
    }

    private void validateWindow(Instant startsAt, Instant endsAt) {
        if (!endsAt.isAfter(startsAt)) {
            throw new ValidationException("endsAt", "must be after startsAt");
        }
    }

    private String resolveSlug(String requested, String title, UUID excludeId) {
        String source = requested != null && !requested.isBlank() ? requested : title;
        String slug = Slugs.slugify(source);
        if (slug.isEmpty()) {
            throw new ValidationException("slug", "could not derive a slug; provide one explicitly");
        }
        eventRepository.findBySlug(slug).ifPresent(existing -> {
            if (excludeId == null || !existing.getId().equals(excludeId)) {
                throw new ValidationException("slug", "already in use");
            }
        });
        return slug;
    }
}
