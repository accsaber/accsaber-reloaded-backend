package com.accsaber.backend.service.mission;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.EventMissionTargets;
import com.accsaber.backend.model.dto.request.mission.MissionTemplateRequest;
import com.accsaber.backend.model.entity.mission.Event;
import com.accsaber.backend.model.entity.mission.MissionPool;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.accsaber.backend.repository.CategoryRepository;
import com.accsaber.backend.repository.CurveRepository;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.repository.mission.EventRepository;
import com.accsaber.backend.repository.mission.MissionTemplateRepository;
import com.accsaber.backend.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionTemplateService {

    private final MissionTemplateRepository templateRepository;
    private final CurveRepository curveRepository;
    private final ItemRepository itemRepository;
    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final MapDifficultyRepository mapDifficultyRepository;
    private final UserRepository userRepository;

    public List<MissionTemplate> listAll() {
        return templateRepository.findAll();
    }

    public List<MissionTemplate> listByEvent(UUID eventId) {
        return templateRepository.findActiveByEvent(eventId);
    }

    public MissionTemplate findById(UUID id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MissionTemplate", id));
    }

    @Transactional
    public MissionTemplate create(MissionTemplateRequest req) {
        MissionTemplate.MissionTemplateBuilder builder = MissionTemplate.builder()
                .code(req.getCode())
                .name(req.getName())
                .description(req.getDescription())
                .type(req.getType())
                .pool(req.getPool())
                .weight(req.getWeight())
                .guaranteedDoable(req.isGuaranteedDoable())
                .targetCountMin(req.getTargetCountMin())
                .targetCountMax(req.getTargetCountMax())
                .active(req.getActive() == null || req.getActive());
        if (req.getXpCurveId() != null) {
            builder.xpCurve(curveRepository.findById(req.getXpCurveId())
                    .orElseThrow(() -> new ResourceNotFoundException("Curve", req.getXpCurveId())));
        }
        if (req.getAwardsItemId() != null) {
            builder.awardsItem(itemRepository.findById(req.getAwardsItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item", req.getAwardsItemId())));
        }
        if (req.getXpMultiplier() != null)
            builder.xpMultiplier(req.getXpMultiplier());
        if (req.getBandEasy() != null)
            builder.bandEasy(req.getBandEasy());
        if (req.getBandMedium() != null)
            builder.bandMedium(req.getBandMedium());
        if (req.getBandHard() != null)
            builder.bandHard(req.getBandHard());
        MissionTemplate template = builder.build();
        applyEventFields(template, req);
        validateEventConsistency(template);
        return templateRepository.save(template);
    }

    @Transactional
    public MissionTemplate update(UUID id, MissionTemplateRequest req) {
        MissionTemplate template = findById(id);
        if (req.getCode() != null)
            template.setCode(req.getCode());
        if (req.getName() != null)
            template.setName(req.getName());
        if (req.getDescription() != null)
            template.setDescription(req.getDescription());
        if (req.getType() != null)
            template.setType(req.getType());
        if (req.getPool() != null)
            template.setPool(req.getPool());
        if (req.getWeight() != null)
            template.setWeight(req.getWeight());
        template.setGuaranteedDoable(req.isGuaranteedDoable());
        if (req.getXpCurveId() != null) {
            template.setXpCurve(curveRepository.findById(req.getXpCurveId())
                    .orElseThrow(() -> new ResourceNotFoundException("Curve", req.getXpCurveId())));
        }
        if (req.getAwardsItemId() != null) {
            template.setAwardsItem(itemRepository.findById(req.getAwardsItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item", req.getAwardsItemId())));
        }
        if (req.getXpMultiplier() != null)
            template.setXpMultiplier(req.getXpMultiplier());
        if (req.getBandEasy() != null)
            template.setBandEasy(req.getBandEasy());
        if (req.getBandMedium() != null)
            template.setBandMedium(req.getBandMedium());
        if (req.getBandHard() != null)
            template.setBandHard(req.getBandHard());
        if (req.getTargetCountMin() != null)
            template.setTargetCountMin(req.getTargetCountMin());
        if (req.getTargetCountMax() != null)
            template.setTargetCountMax(req.getTargetCountMax());
        if (req.getActive() != null)
            template.setActive(req.getActive());
        applyEventFields(template, req);
        validateEventConsistency(template);
        return templateRepository.save(template);
    }

    @Transactional
    public void delete(UUID id) {
        MissionTemplate template = findById(id);
        template.setActive(false);
        templateRepository.save(template);
    }

    private void applyEventFields(MissionTemplate template, MissionTemplateRequest req) {
        if (req.getEventId() != null) {
            template.setEvent(eventRepository.findById(req.getEventId())
                    .orElseThrow(() -> new ResourceNotFoundException("Event", req.getEventId())));
        }
        if (req.getUnlocksAt() != null)
            template.setUnlocksAt(req.getUnlocksAt());
        if (req.getCompletableUntil() != null)
            template.setCompletableUntil(req.getCompletableUntil());
        if (req.getRepeatable() != null)
            template.setRepeatable(req.getRepeatable());
        if (req.getMaxCompletions() != null)
            template.setMaxCompletions(req.getMaxCompletions());
        if (req.getFixedXp() != null)
            template.setFixedXp(req.getFixedXp());
        if (req.getTargets() != null) {
            validateTargetRefs(req.getTargets());
            template.setEventTargets(req.getTargets());
        }
    }

    private void validateTargetRefs(EventMissionTargets targets) {
        if (targets.categoryId() != null && !categoryRepository.existsById(targets.categoryId())) {
            throw new ResourceNotFoundException("Category", targets.categoryId());
        }
        if (targets.mapDifficultyId() != null && !mapDifficultyRepository.existsById(targets.mapDifficultyId())) {
            throw new ResourceNotFoundException("MapDifficulty", targets.mapDifficultyId());
        }
        if (targets.playerId() != null) {
            Long playerId;
            try {
                playerId = targets.playerIdAsLong();
            } catch (NumberFormatException e) {
                throw new ValidationException("targets.playerId", "must be a numeric Steam ID");
            }
            if (!userRepository.existsById(playerId)) {
                throw new ResourceNotFoundException("User", playerId);
            }
        }
    }

    private void validateEventConsistency(MissionTemplate template) {
        Event event = template.getEvent();
        if (event == null) {
            if (template.getPool() == MissionPool.event) {
                throw new ValidationException("eventId", "required for event pool templates");
            }
            return;
        }
        if (template.getPool() != MissionPool.event) {
            throw new ValidationException("pool", "must be event when eventId is set");
        }
        if (template.getUnlocksAt() != null
                && (template.getUnlocksAt().isBefore(event.getStartsAt())
                        || !template.getUnlocksAt().isBefore(event.getEndsAt()))) {
            throw new ValidationException("unlocksAt", "must fall within the event window");
        }
        if (template.getCompletableUntil() != null) {
            Instant unlock = template.getUnlocksAt() != null ? template.getUnlocksAt() : event.getStartsAt();
            if (!template.getCompletableUntil().isAfter(unlock)
                    || template.getCompletableUntil().isAfter(event.getEndsAt())) {
                throw new ValidationException("completableUntil",
                        "must be after the unlock and no later than the event end");
            }
        }
        if (template.getMaxCompletions() != null && !template.isRepeatable()) {
            throw new ValidationException("maxCompletions", "only valid on repeatable missions");
        }
    }
}
