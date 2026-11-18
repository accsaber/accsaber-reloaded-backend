package com.accsaber.backend.service.clan.war;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.accsaber.backend.config.ClanProperties;
import com.accsaber.backend.model.entity.clan.war.ClanWarTeam;
import com.accsaber.backend.model.entity.user.UserSettingKey;
import com.accsaber.backend.service.clan.ClanStrengthService;
import com.accsaber.backend.service.clan.ClanStrengthService.MemberStrength;
import com.accsaber.backend.service.player.UserSettingsService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class ClanWarOutmatch {

    private final ClanStrengthService strengthService;
    private final UserSettingsService settingsService;
    private final ClanProperties clanProperties;

    static double heft(double rawSkill) {
        double skill = ClanWarDamage.skill(rawSkill);
        return skill * skill;
    }

    Map<ClanWarTeam, Double> worth(UUID attackerClanId, UUID defenderClanId, Map<UUID, Double> clanHeft) {
        Map<ClanWarTeam, Double> worth = new EnumMap<>(ClanWarTeam.class);
        for (ClanWarTeam team : ClanWarTeam.values()) {
            boolean attack = team == ClanWarTeam.attack;
            double own = clanHeft.getOrDefault(attack ? attackerClanId : defenderClanId, 0.0);
            double floor = clanProperties.getWar().getMinLeadWeight()
                    * clanHeft.getOrDefault(attack ? defenderClanId : attackerClanId, 0.0);
            worth.put(team, own > 0 && own < floor ? floor / own : 1.0);
        }
        return worth;
    }

    Map<ClanWarTeam, Double> forecast(UUID attackerClanId, UUID defenderClanId) {
        List<MemberStrength> members = strengthService.memberStrengths(List.of(attackerClanId, defenderClanId), null);
        Map<Long, Boolean> inWars = settingsService.getMany(members.stream().map(MemberStrength::userId).toList(),
                UserSettingKey.CLANS_WAR_PARTICIPATION, Boolean.class);
        return worth(attackerClanId, defenderClanId,
                clanHeft(members.stream().filter(member -> inWars.get(member.userId())).toList()));
    }

    static Map<UUID, Double> clanHeft(Collection<MemberStrength> members) {
        return members.stream().collect(Collectors.groupingBy(MemberStrength::clanId,
                Collectors.summingDouble(member -> heft(member.skill()))));
    }
}
