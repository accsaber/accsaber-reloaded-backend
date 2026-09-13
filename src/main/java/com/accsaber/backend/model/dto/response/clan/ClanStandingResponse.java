package com.accsaber.backend.model.dto.response.clan;

public record ClanStandingResponse(PublicClanResponse clan, long rank, double standing, double baseStanding,
        double earned) {
}
