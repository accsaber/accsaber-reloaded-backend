package com.accsaber.backend.config;

import java.time.Duration;
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
    private int founderInactivityDays = 365;
    private double playClanXp = 3.0;
    private double standingPerSkill = 10.0;
    private double rosterReferenceStrength = 280.0;
    private double rosterReferenceMembers = 5.0;
    private double rosterFactorExponent = 0.5;
    private int missionSlots = 3;
    private int missionClears = 1;
    private double missionXp = 69.0;
    private double missionStanding = 100.0;
    private double missionContribution = 150.0;
    private double missionMinShare = 0.5;
    private List<TrustTier> trustTiers = List.of(
            new TrustTier(Duration.ZERO, 0.0, 1),
            new TrustTier(Duration.ofDays(30), 100.0, 2),
            new TrustTier(Duration.ofDays(120), 600.0, 3),
            new TrustTier(Duration.ofDays(365), 3000.0, 4));
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
        private double maxUnderdogShare = 0.85;
        private double guard = 100.0;
        private double baseDamage = 35.0;
        private double escalation = 1.5;
        private double disparityExponent = 1.0;
        private double missingScoreMultiplier = 0.35;
        private double breakShare = 0.8;
        private double breakDecay = 0.75;
        private double breakContribution = 50.0;
        private double breakXp = 250.0;
        private double breakClanXp = 23.0;
        private double winClanXp = 345.0;
        private double xpPerContribution = 1.0;
        private double warSizeExponent = 0.6;
        private double loanBreakClanXp = 6.0;
        private Duration loanCooldown = Duration.ofDays(3);
    }
}
