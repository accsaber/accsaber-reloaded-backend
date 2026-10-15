package com.accsaber.backend.service.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.accsaber.backend.model.admin.JobRecord;
import com.accsaber.backend.model.admin.JobStatus;
import com.accsaber.backend.model.admin.JobType;

class JobRegistryTest {

    private JobRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new JobRegistry();
    }

    @Nested
    class Lifecycle {

        @Test
        void aStartedJobIsRunningAndFindable() {
            JobRecord job = registry.start(JobType.RECALCULATE_AP_ALL, null);

            assertThat(registry.find(job.id())).isPresent()
                    .get()
                    .satisfies(found -> {
                        assertThat(found.status()).isEqualTo(JobStatus.RUNNING);
                        assertThat(found.finishedAt()).isNull();
                    });
            assertThat(registry.isRunning(JobType.RECALCULATE_AP_ALL)).isTrue();
        }

        @Test
        void succeedingMovesItOutOfRunningAndStampsFinishedAt() {
            JobRecord job = registry.start(JobType.BACKFILL_SCORES_ALL, null);

            registry.succeed(job.id());

            assertThat(registry.isRunning(JobType.BACKFILL_SCORES_ALL)).isFalse();
            assertThat(registry.find(job.id())).get().satisfies(found -> {
                assertThat(found.status()).isEqualTo(JobStatus.SUCCEEDED);
                assertThat(found.finishedAt()).isNotNull();
                assertThat(found.error()).isNull();
            });
        }

        @Test
        void failingKeepsTheErrorMessage() {
            JobRecord job = registry.start(JobType.BACKFILL_CDN_AVATARS, "force=true");

            registry.fail(job.id(), new IllegalStateException("upstream refused"));

            assertThat(registry.find(job.id())).get().satisfies(found -> {
                assertThat(found.status()).isEqualTo(JobStatus.FAILED);
                assertThat(found.error()).isEqualTo("upstream refused");
                assertThat(found.detail()).isEqualTo("force=true");
            });
        }

        @Test
        void finishingAnUnknownJobIsIgnored() {
            registry.succeed(UUID.randomUUID());

            assertThat(registry.list()).isEmpty();
        }

        @Test
        void anErrorWithNoMessageStillRecordsSomething() {
            JobRecord job = registry.start(JobType.RECALCULATE_XP_TOTALS, null);

            registry.fail(job.id(), new IllegalStateException());

            assertThat(registry.find(job.id())).get()
                    .satisfies(found -> assertThat(found.error()).isEqualTo("IllegalStateException"));
        }
    }

    @Nested
    class Listing {

        @Test
        void runningJobsComeBeforeFinishedOnes() {
            JobRecord finished = registry.start(JobType.RECALCULATE_AP_RAW, null);
            registry.succeed(finished.id());
            JobRecord active = registry.start(JobType.BACKFILL_SCORES_ALL, null);

            assertThat(registry.list()).extracting(JobRecord::id)
                    .containsExactly(active.id(), finished.id());
        }

        @Test
        void recentHistoryIsBoundedSoItCannotGrowForever() {
            for (int i = 0; i < 60; i++) {
                registry.succeed(registry.start(JobType.REGENERATE_SONG_SUGGEST, "run " + i).id());
            }

            assertThat(registry.list()).hasSize(50);
            assertThat(registry.list().get(0).detail()).isEqualTo("run 59");
        }
    }
}
