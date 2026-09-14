package com.accsaber.backend.config;

import java.time.Duration;
import java.time.Period;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "accsaber.clans")
@Getter
@Setter
public class ClanProperties {

    private Duration joinCooldown = Duration.ofDays(14);
    private int maxMembers = 50;
    private int founderInactivityDays = 30;
    private Period seasonLength = Period.ofMonths(6);
    private double playXpShare = 0.05;
    private double standingPerSkill = 10.0;
    private double rosterReferenceStrength = 300.0;
    private int missionClears = 3;
    private double missionXp = 400.0;
    private double missionStanding = 40.0;
    private double missionContribution = 100.0;
    private List<TrustTier> trustTiers = List.of(
            new TrustTier(Duration.ZERO, 0.0, 1),
            new TrustTier(Duration.ofDays(30), 500.0, 2),
            new TrustTier(Duration.ofDays(120), 2500.0, 3),
            new TrustTier(Duration.ofDays(365), 10000.0, 4));
    private War war = new War();

    public record TrustTier(Duration minAge, double minContribution, int loanCap) {
    }

    @Getter
    @Setter
    public static class War {
        private Duration pickWindow = Duration.ofHours(24);
        private Duration prepDuration = Duration.ofHours(48);
        private int drawAfterQuietDays = 7;
        private double punchDownFloor = 0.5;
        private int poolSize = 10;
        private double maxUnderdogShare = 0.7;
        private double guard = 100.0;
        private double baseDamage = 25.0;
        private double escalation = 1.5;
        private double disparityExponent = 1.0;
        private double missingScoreMultiplier = 0.5;
        private double breakShare = 0.2;
        private double breakDecay = 0.5;
        private double breakContribution = 50.0;
        private double loanXpShare = 0.25;
        private Duration loanCooldown = Duration.ofDays(3);
    }
}
