package com.accsaber.backend.service.news;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accsaber.backend.exception.ConflictException;
import com.accsaber.backend.exception.ForbiddenException;
import com.accsaber.backend.exception.ResourceNotFoundException;
import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.dto.request.news.CreateNewsRequest;
import com.accsaber.backend.model.dto.request.news.UpdateNewsRequest;
import com.accsaber.backend.model.dto.response.news.NewsResponse;
import com.accsaber.backend.model.dto.response.news.PublicNewsResponse;
import com.accsaber.backend.model.entity.Curve;
import com.accsaber.backend.model.entity.campaign.Campaign;
import com.accsaber.backend.model.entity.map.Batch;
import com.accsaber.backend.model.entity.milestone.MilestoneSet;
import com.accsaber.backend.model.entity.news.News;
import com.accsaber.backend.model.entity.news.NewsStatus;
import com.accsaber.backend.model.entity.news.NewsType;
import com.accsaber.backend.model.entity.staff.StaffRole;
import com.accsaber.backend.model.entity.staff.StaffUser;
import com.accsaber.backend.repository.CurveRepository;
import com.accsaber.backend.repository.campaign.CampaignRepository;
import com.accsaber.backend.repository.map.BatchRepository;
import com.accsaber.backend.repository.milestone.MilestoneSetRepository;
import com.accsaber.backend.repository.news.NewsRepository;
import com.accsaber.backend.repository.staff.StaffUserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NewsService {

    private static final Pattern SLUG_INVALID_CHARS = Pattern.compile("[^a-z0-9-]+");
    private static final Pattern SLUG_DASH_RUNS = Pattern.compile("-{2,}");

    private final NewsRepository newsRepository;
    private final StaffUserRepository staffUserRepository;
    private final BatchRepository batchRepository;
    private final CampaignRepository campaignRepository;
    private final MilestoneSetRepository milestoneSetRepository;
    private final CurveRepository curveRepository;

    public Page<PublicNewsResponse> findPublic(NewsType type, Pageable pageable) {
        return newsRepository
                .search(NewsStatus.PUBLISHED, null, type != null ? type.filterCode() : 0, applyPublicSort(pageable))
                .map(NewsService::toPublicResponse);
    }

    public PublicNewsResponse findPublicBySlug(String slug) {
        News news = newsRepository.findBySlugAndActiveTrue(slug)
                .filter(n -> n.getStatus() == NewsStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("News", slug));
        return toPublicResponse(news);
    }

    public PublicNewsResponse findPublicById(UUID id) {
        News news = newsRepository.findByIdAndActiveTrue(id)
                .filter(n -> n.getStatus() == NewsStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("News", id));
        return toPublicResponse(news);
    }

    public Page<NewsResponse> findStaffAll(NewsStatus status, NewsType type, Pageable pageable) {
        return newsRepository
                .search(status, null, type != null ? type.filterCode() : 0, pageable)
                .map(NewsService::toResponse);
    }

    public Page<NewsResponse> findStaffByAuthor(UUID staffUserId, NewsStatus status, NewsType type, Pageable pageable) {
        return newsRepository
                .search(status, staffUserId, type != null ? type.filterCode() : 0, pageable)
                .map(NewsService::toResponse);
    }

    public NewsResponse findStaffById(UUID id) {
        return toResponse(loadActive(id));
    }

    @Transactional
    public NewsResponse create(CreateNewsRequest request, UUID staffUserId) {
        StaffUser staffUser = staffUserRepository.findById(staffUserId)
                .orElseThrow(() -> new ResourceNotFoundException("StaffUser", staffUserId));

        String slug = resolveSlug(request.getSlug(), request.getTitle(), null);
        NewsStatus status = request.getStatus() != null ? request.getStatus() : NewsStatus.DRAFT;

        News news = News.builder()
                .staffUser(staffUser)
                .title(request.getTitle())
                .slug(slug)
                .description(request.getDescription())
                .content(request.getContent())
                .imageUrl(request.getImageUrl())
                .status(status)
                .pinned(Boolean.TRUE.equals(request.getPinned()))
                .publishedAt(status == NewsStatus.PUBLISHED ? Instant.now() : null)
                .batch(resolveBatch(request.getBatchId()))
                .campaign(resolveCampaign(request.getCampaignId()))
                .milestoneSet(resolveMilestoneSet(request.getMilestoneSetId()))
                .curve(resolveCurve(request.getCurveId()))
                .build();

        return toResponse(newsRepository.save(news));
    }

    @Transactional
    public NewsResponse update(UUID id, UpdateNewsRequest request, UUID staffUserId, StaffRole role) {
        News news = loadActive(id);
        ensureCanEdit(news, staffUserId, role);

        if (request.getTitle() != null) {
            news.setTitle(request.getTitle());
        }
        if (request.getSlug() != null) {
            news.setSlug(resolveSlug(request.getSlug(), null, id));
        }
        if (request.getDescription() != null) {
            news.setDescription(request.getDescription());
        }
        if (request.getContent() != null) {
            news.setContent(request.getContent());
        }
        if (request.getImageUrl() != null) {
            news.setImageUrl(request.getImageUrl());
        }
        if (request.getPinned() != null) {
            news.setPinned(request.getPinned());
        }
        if (request.getStatus() != null) {
            applyStatusChange(news, request.getStatus());
        }
        if (request.getBatchId() != null) {
            news.setBatch(resolveBatch(request.getBatchId()));
        }
        if (request.getCampaignId() != null) {
            news.setCampaign(resolveCampaign(request.getCampaignId()));
        }
        if (request.getMilestoneSetId() != null) {
            news.setMilestoneSet(resolveMilestoneSet(request.getMilestoneSetId()));
        }
        if (request.getCurveId() != null) {
            news.setCurve(resolveCurve(request.getCurveId()));
        }

        return toResponse(newsRepository.save(news));
    }

    @Transactional
    public void deactivate(UUID id) {
        News news = loadActive(id);
        news.setActive(false);
        newsRepository.save(news);
    }

    @Transactional
    public void hardDelete(UUID id) {
        News news = newsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("News", id));
        newsRepository.delete(news);
    }

    private News loadActive(UUID id) {
        return newsRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("News", id));
    }

    private void ensureCanEdit(News news, UUID staffUserId, StaffRole role) {
        if (role == StaffRole.ADMIN) {
            return;
        }
        if (!news.getStaffUser().getId().equals(staffUserId)) {
            throw new ForbiddenException("You can only edit news you authored");
        }
    }

    private void applyStatusChange(News news, NewsStatus newStatus) {
        if (news.getStatus() == newStatus) {
            return;
        }
        news.setStatus(newStatus);
        if (newStatus == NewsStatus.PUBLISHED && news.getPublishedAt() == null) {
            news.setPublishedAt(Instant.now());
        }
    }

    private String resolveSlug(String requested, String fallbackTitle, UUID currentId) {
        String base = (requested != null && !requested.isBlank())
                ? requested.trim()
                : (fallbackTitle != null ? fallbackTitle : "");
        String slug = slugify(base);
        if (slug.isEmpty()) {
            throw new ValidationException("Slug cannot be empty");
        }
        boolean taken = currentId == null
                ? newsRepository.existsBySlug(slug)
                : newsRepository.existsBySlugAndIdNot(slug, currentId);
        if (taken) {
            throw new ConflictException("News", "slug=" + slug);
        }
        return slug;
    }

    private static String slugify(String input) {
        String lower = input.toLowerCase().trim().replace(' ', '-');
        String cleaned = SLUG_INVALID_CHARS.matcher(lower).replaceAll("");
        cleaned = SLUG_DASH_RUNS.matcher(cleaned).replaceAll("-");
        if (cleaned.startsWith("-")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.endsWith("-")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    private Batch resolveBatch(UUID batchId) {
        if (batchId == null)
            return null;
        return batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Batch", batchId));
    }

    private Campaign resolveCampaign(UUID campaignId) {
        if (campaignId == null)
            return null;
        return campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));
    }

    private MilestoneSet resolveMilestoneSet(UUID milestoneSetId) {
        if (milestoneSetId == null)
            return null;
        return milestoneSetRepository.findById(milestoneSetId)
                .orElseThrow(() -> new ResourceNotFoundException("MilestoneSet", milestoneSetId));
    }

    private Curve resolveCurve(UUID curveId) {
        if (curveId == null)
            return null;
        return curveRepository.findById(curveId)
                .orElseThrow(() -> new ResourceNotFoundException("Curve", curveId));
    }

    private static Pageable applyPublicSort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        Sort sort = Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("publishedAt"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private static NewsResponse toResponse(News news) {
        StaffUser author = news.getStaffUser();
        return NewsResponse.builder()
                .id(news.getId())
                .staffUserId(author != null ? author.getId() : null)
                .staffUsername(author != null ? author.getUsername() : null)
                .title(news.getTitle())
                .slug(news.getSlug())
                .description(news.getDescription())
                .content(news.getContent())
                .imageUrl(news.getImageUrl())
                .status(news.getStatus())
                .type(NewsType.of(news))
                .pinned(news.isPinned())
                .batchId(news.getBatch() != null ? news.getBatch().getId() : null)
                .campaignId(news.getCampaign() != null ? news.getCampaign().getId() : null)
                .milestoneSetId(news.getMilestoneSet() != null ? news.getMilestoneSet().getId() : null)
                .curveId(news.getCurve() != null ? news.getCurve().getId() : null)
                .publishedAt(news.getPublishedAt())
                .active(news.isActive())
                .createdAt(news.getCreatedAt())
                .updatedAt(news.getUpdatedAt())
                .build();
    }

    private static PublicNewsResponse toPublicResponse(News news) {
        StaffUser author = news.getStaffUser();
        return PublicNewsResponse.builder()
                .id(news.getId())
                .authorName(author != null ? author.getUsername() : null)
                .title(news.getTitle())
                .slug(news.getSlug())
                .description(news.getDescription())
                .content(news.getContent())
                .imageUrl(news.getImageUrl())
                .type(NewsType.of(news))
                .pinned(news.isPinned())
                .batchId(news.getBatch() != null ? news.getBatch().getId() : null)
                .campaignId(news.getCampaign() != null ? news.getCampaign().getId() : null)
                .milestoneSetId(news.getMilestoneSet() != null ? news.getMilestoneSet().getId() : null)
                .curveId(news.getCurve() != null ? news.getCurve().getId() : null)
                .publishedAt(news.getPublishedAt())
                .build();
    }
}
