package com.accsaber.backend.scheduler;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.accsaber.backend.service.songsuggest.SongSuggestService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SongSuggestScheduler {

    private final SongSuggestService songSuggestService;

    @Scheduled(cron = "${accsaber.scheduler.songsuggest-cron:0 0 4 * * MON}")
    public void regenerateWeekly() {
        songSuggestService.regenerateAsync();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void regenerateOnStartup() {
        songSuggestService.regenerateAsync();
    }
}
