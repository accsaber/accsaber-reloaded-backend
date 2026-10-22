package com.accsaber.backend.config;

import java.time.Duration;

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
    private Duration firstMissionDelay = Duration.ofDays(2);
    private double missionXp = 70.0;
    private double missionStanding = 100.0;
    private double missionContribution = 150.0;
    private double missionMinShare = 0.5;
    private Duration pacifistDelay = Duration.ofDays(7);
    private War war = new War();

    @Getter
    @Setter
    public static class War {
        private Duration pickWindow = Duration.ofHours(24);
        private Duration prepDuration = Duration.ofHours(24);
        private int drawAfterQuietDays = 7;
        private double punchDownFloor = 0.5;
        private int poolSize = 10;
        private double maxUnderdogShare = 0.85;
        private double guard = 100.0;
        private double baseDamage = 15.0;
        private double escalation = 1.5;
        private double disparityExponent = 1.0;
        private double missingScoreMultiplier = 0.3;
        private double missingScoreEscalation = 1.2;
        private double breakShare = 0.5;
        private double breakDecay = 0.75;
        private double breakContribution = 50.0;
        private double breakXp = 50.0;
        private double breakClanXp = 6.0;
        private double winClanXp = 345.0;
        private double xpPerContribution = 0.2;
        private double warSizeExponent = 0.6;
        private double chipDamage = 3.0;
        private double chipWindow = 0.3;
        private double chipSkillExponent = 2.0;
        private double minChipDamage = 1.0;
        private double extraChipChance = 0.25;
        private double allyStakeShare = 0.3;
        private Duration maxDuration = Duration.ofDays(28);
        private Duration engagementWindow = Duration.ofDays(14);
        private double engagementFull = 0.5;
        private Duration redeclareCooldown = Duration.ofDays(7);
        private Duration retreatWindow = Duration.ofHours(24);
    }
}
