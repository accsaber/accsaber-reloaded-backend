package com.accsaber.backend.service.mission;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.model.dto.request.mission.MissionTemplateRequest;
import com.accsaber.backend.model.entity.mission.MissionTemplate;
import com.accsaber.backend.repository.CurveRepository;
import com.accsaber.backend.repository.item.ItemRepository;
import com.accsaber.backend.repository.mission.MissionTemplateRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionTemplateService {

    private final MissionTemplateRepository templateRepository;
    private final CurveRepository curveRepository;
    private final ItemRepository itemRepository;

    public List<MissionTemplate> listAll() {
        return templateRepository.findAll();
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
        return templateRepository.save(builder.build());
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
        return templateRepository.save(template);
    }

    @Transactional
    public void delete(UUID id) {
        MissionTemplate template = findById(id);
        template.setActive(false);
        templateRepository.save(template);
    }
}
