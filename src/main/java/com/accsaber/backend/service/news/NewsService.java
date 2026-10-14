package com.accsaber.backend.service.news;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
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
import com.accsaber.backend.util.Slugs;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NewsService {

    private final NewsRepository newsRepository;
    private final StaffUserRepository staffUserRepository;
    private final BatchRepository batchRepository;
    private final CampaignRepository campaignRepository;
    private final MilestoneSetRepository milestoneSetRepository;
    private final CurveRepository curveRepository;

    public Page<PublicNewsResponse> findPublic(NewsType type, String search, Pageable pageable) {
        String term = search == null || search.isBlank() ? null : search.trim();
        return newsRepository
                .search(NewsStatus.PUBLISHED, null, type != null ? type.filterCode() : 0, term, applyPublicSort(pageable))
                .map(NewsService::toPublicResponse);
    }

    public PublicNewsResponse findPublic(String idOrSlug) {
        UUID id = Slugs.uuidOrNull(idOrSlug);
        News news = (id != null
                ? newsRepository.findByIdAndActiveTrue(id)
                : newsRepository.findBySlugAndActiveTrue(idOrSlug))
                .filter(n -> n.getStatus() == NewsStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("News", idOrSlug));
        return toPublicResponse(news);
    }

    public Page<NewsResponse> findStaff(UUID authorId, NewsStatus status, NewsType type, Pageable pageable) {
        return newsRepository
                .search(status, authorId, type != null ? type.filterCode() : 0, null, pageable)
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
                .batch(resolve(batchRepository, request.getBatchId(), "Batch"))
                .campaign(resolve(campaignRepository, request.getCampaignId(), "Campaign"))
                .milestoneSet(resolve(milestoneSetRepository, request.getMilestoneSetId(), "MilestoneSet"))
                .curve(resolve(curveRepository, request.getCurveId(), "Curve"))
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
            news.setBatch(resolve(batchRepository, request.getBatchId(), "Batch"));
        }
        if (request.getCampaignId() != null) {
            news.setCampaign(resolve(campaignRepository, request.getCampaignId(), "Campaign"));
        }
        if (request.getMilestoneSetId() != null) {
            news.setMilestoneSet(resolve(milestoneSetRepository, request.getMilestoneSetId(), "MilestoneSet"));
        }
        if (request.getCurveId() != null) {
            news.setCurve(resolve(curveRepository, request.getCurveId(), "Curve"));
        }

        return toResponse(newsRepository.save(news));
    }

    public void assertCanEdit(UUID id, UUID staffUserId, StaffRole role) {
        ensureCanEdit(loadActive(id), staffUserId, role);
    }

    @Transactional
    public NewsResponse setImageUrl(UUID id, String url, UUID staffUserId, StaffRole role) {
        News news = loadActive(id);
        ensureCanEdit(news, staffUserId, role);
        news.setImageUrl(url);
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
        String slug = Slugs.slugify(base);
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

    private static <T> T resolve(JpaRepository<T, UUID> repository, UUID id, String resource) {
        if (id == null)
            return null;
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(resource, id));
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
