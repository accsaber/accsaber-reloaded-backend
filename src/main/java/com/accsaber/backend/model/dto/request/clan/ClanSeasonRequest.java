package com.accsaber.backend.model.dto.request.clan;

import java.time.Instant;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ClanSeasonRequest {

    @Size(min = 1, max = 64)
    private String name;

    @Size(min = 1, max = 64)
    private String slug;

    private Instant startsAt;

    private Instant endsAt;
}
