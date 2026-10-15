package com.accsaber.backend.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.accsaber.backend.exception.ValidationException;
import com.accsaber.backend.model.admin.JobField;
import com.accsaber.backend.model.admin.JobType;
import com.accsaber.backend.model.dto.request.admin.RunJobRequest;
import com.accsaber.backend.model.dto.response.admin.JobTypeResponse;
import com.accsaber.backend.repository.map.MapDifficultyRepository;
import com.accsaber.backend.service.media.CdnSyncService;
import com.accsaber.backend.service.milestone.MilestoneService;
import com.accsaber.backend.service.score.ScoreImportService;
import com.accsaber.backend.service.score.ScoreIngestionService;
import com.accsaber.backend.service.score.ScoreRecalculationService;
import com.accsaber.backend.service.score.XPReweightService;
import com.accsaber.backend.service.songsuggest.SongSuggestService;

@ExtendWith(MockitoExtension.class)
class JobCatalogueTest {

    @Mock
    private JobRegistry registry;
    @Mock
    private ScoreRecalculationService scoreRecalculationService;
    @Mock
    private XPReweightService xpReweightService;
    @Mock
    private ScoreImportService scoreImportService;
    @Mock
    private ScoreIngestionService scoreIngestionService;
    @Mock
    private CdnSyncService cdnSyncService;
    @Mock
    private MilestoneService milestoneService;
    @Mock
    private SongSuggestService songSuggestService;
    @Mock
    private MapDifficultyRepository mapDifficultyRepository;

    @InjectMocks
    private AdminJobService service;

    @Test
    void everyJobTypeIsDescribed() {
        assertThat(service.catalogue())
                .hasSize(JobType.values().length)
                .allSatisfy(entry -> {
                    assertThat(entry.getGroup()).isNotNull();
                    assertThat(entry.getLabel()).isNotBlank();
                    assertThat(entry.getDescription()).isNotBlank();
                });
    }

    @Test
    void everyFieldIsReadableAndUniquelyKeyed() {
        for (JobTypeResponse entry : service.catalogue()) {
            Set<String> keys = new HashSet<>();
            assertThat(entry.getFields()).allSatisfy(field -> {
                assertThat(field.getKind()).isNotNull();
                assertThat(field.getLabel()).isNotBlank();
                assertThat(keys.add(field.getKey())).isTrue();
                assertThat(hasReadableProperty(field.getKey())).isTrue();
            });
        }
    }

    @Test
    void fieldReadersReturnTheValueTheirKeyNames() {
        RunJobRequest request = new RunJobRequest();
        request.setUserId(76561198087536397L);
        JobField userId = JobType.BACKFILL_SCORES_USER.getFields().getFirst();

        assertThat(userId.key()).isEqualTo("userId");
        assertThat(userId.reader().apply(request)).isEqualTo(76561198087536397L);
    }

    @Test
    void requiredFieldsAreRejectedBeforeTheJobStarts() {
        for (JobType type : JobType.values()) {
            JobField missing = type.getFields().stream().filter(JobField::required).findFirst().orElse(null);
            if (missing == null) {
                continue;
            }
            RunJobRequest request = new RunJobRequest();
            request.setType(type);

            assertThatThrownBy(() -> service.run(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining(missing.key());
        }
    }

    private boolean hasReadableProperty(String key) {
        String suffix = Character.toUpperCase(key.charAt(0)) + key.substring(1);
        return java.util.Arrays.stream(RunJobRequest.class.getMethods())
                .anyMatch(m -> m.getParameterCount() == 0
                        && (m.getName().equals("get" + suffix) || m.getName().equals("is" + suffix)));
    }
}
