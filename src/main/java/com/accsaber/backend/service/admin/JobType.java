package com.accsaber.backend.service.admin;

import java.util.List;

import com.accsaber.backend.model.dto.request.admin.RunJobRequest;

import lombok.Getter;

@Getter
public enum JobType {

    RECALCULATE_AP_DIFFICULTY(JobGroup.RECALCULATION, "Recalculate AP for one difficulty",
            "Redoes raw and weighted AP for every score on a single map difficulty.",
            JobField.required("difficultyId", JobFieldKind.MAP_DIFFICULTY, "Map difficulty",
                    RunJobRequest::getDifficultyId)),

    RECALCULATE_AP_DIFFICULTIES(JobGroup.RECALCULATION, "Recalculate AP for several difficulties",
            "Same as the single difficulty run, just over a list of them in one go.",
            JobField.requiredList("difficultyIds", JobFieldKind.MAP_DIFFICULTY, "Map difficulties",
                    RunJobRequest::getDifficultyIds)),

    RECALCULATE_AP_RAW(JobGroup.RECALCULATION, "Recalculate raw AP everywhere",
            "Rebuilds raw AP for every score on the site. This one is heavy."),

    RECALCULATE_AP_WEIGHTED(JobGroup.RECALCULATION, "Recalculate weighted AP everywhere",
            "Reapplies the weight curve to everyone's ranked plays. Run this after a weight curve change."),

    RECALCULATE_AP_ALL(JobGroup.RECALCULATION, "Recalculate all AP",
            "Raw and weighted together, for everything. The heaviest job we have."),

    RECALCULATE_XP_SCORES(JobGroup.RECALCULATION, "Reweight XP on every score",
            "Recomputes the XP each score was worth. Run this after the XP curve changes."),

    RECALCULATE_XP_TOTALS(JobGroup.RECALCULATION, "Rebuild XP totals",
            "Re-adds every user's XP from scratch, out of their scores, milestones, set bonuses, campaigns,"
                    + " missions and event bonuses. Use it when totals have drifted."),

    RECALCULATE_XP_USER(JobGroup.RECALCULATION, "Rebuild one player's XP totals",
            "The same rebuild as above, for a single player. Reach for this after a merge or an unmerge.",
            JobField.required("userId", JobFieldKind.USER, "Player", RunJobRequest::getUserId)),

    BACKFILL_SCORES_ALL(JobGroup.SCORE_BACKFILL, "Backfill every ranked difficulty",
            "Pulls scores from BeatLeader and ScoreSaber for the whole ranked pool. This takes hours."),

    BACKFILL_SCORES_DIFFICULTY(JobGroup.SCORE_BACKFILL, "Backfill one difficulty",
            "Pulls the full leaderboard for a single map difficulty and imports what we are missing.",
            JobField.required("difficultyId", JobFieldKind.MAP_DIFFICULTY, "Map difficulty",
                    RunJobRequest::getDifficultyId)),

    BACKFILL_SCORES_DIFFICULTIES(JobGroup.SCORE_BACKFILL, "Backfill several difficulties",
            "The same import over a list of difficulties, a few at a time.",
            JobField.requiredList("difficultyIds", JobFieldKind.MAP_DIFFICULTY, "Map difficulties",
                    RunJobRequest::getDifficultyIds)),

    BACKFILL_SCORES_USER(JobGroup.SCORE_BACKFILL, "Backfill one player",
            "Pulls every ranked score one player has and imports whatever we do not already hold.",
            JobField.required("userId", JobFieldKind.USER, "Player", RunJobRequest::getUserId)),

    BACKFILL_SCORES_USERS(JobGroup.SCORE_BACKFILL, "Backfill several players",
            "The same per player import, run across a list of them.",
            JobField.requiredList("userIds", JobFieldKind.USER, "Players", RunJobRequest::getUserIds)),

    BACKFILL_SCORES_GAP_FILL(JobGroup.SCORE_BACKFILL, "Fill an ingestion gap",
            "Re-pulls scores set after a point in time, for when the websocket feed dropped out.",
            JobField.required("since", JobFieldKind.INSTANT, "Set after", RunJobRequest::getSince),
            JobField.optional("platform", JobFieldKind.PLATFORM, "Platform",
                    "Leave it off to cover both platforms.", RunJobRequest::getPlatform)),

    BACKFILL_CAMPAIGN_LEGACY(JobGroup.CAMPAIGN, "Re-check a legacy campaign",
            "Fetches each node's map from the platforms for players who have no score stored yet, then settles"
                    + " the campaign. The campaign has to be flagged legacy.",
            JobField.required("campaignId", JobFieldKind.CAMPAIGN, "Campaign", RunJobRequest::getCampaignId),
            JobField.optional("userId", JobFieldKind.USER, "Player",
                    "Leave it off to sweep every participant.", RunJobRequest::getUserId)),

    RESETTLE_CAMPAIGN(JobGroup.CAMPAIGN, "Re-settle a campaign",
            "Re-runs campaign evaluation over the scores we already hold, with no platform calls. Use it after a"
                    + " progression fix so people who are already finished get marked as finished.",
            JobField.required("campaignId", JobFieldKind.CAMPAIGN, "Campaign", RunJobRequest::getCampaignId),
            JobField.optional("userId", JobFieldKind.USER, "Player",
                    "Leave it off to sweep every participant.", RunJobRequest::getUserId)),

    BACKFILL_CDN_MAP_COVERS(JobGroup.CDN, "Mirror map covers",
            "Copies map cover art onto our CDN for anything that is missing it.",
            JobField.optional("force", JobFieldKind.FLAG, "Re-mirror everything",
                    "Turn this on to redo files that already look fine.", RunJobRequest::isForce)),

    BACKFILL_CDN_AVATARS(JobGroup.CDN, "Mirror player avatars",
            "Copies player avatars onto our CDN for anything that is missing it.",
            JobField.optional("force", JobFieldKind.FLAG, "Re-mirror everything",
                    "Turn this on to redo files that already look fine.", RunJobRequest::isForce)),

    BACKFILL_MILESTONE(JobGroup.MILESTONE, "Backfill one milestone",
            "Awards a single milestone to everyone who already qualifies for it.",
            JobField.required("milestoneId", JobFieldKind.MILESTONE, "Milestone",
                    RunJobRequest::getMilestoneId)),

    BACKFILL_MILESTONES_ALL(JobGroup.MILESTONE, "Backfill every milestone",
            "Runs the whole milestone set against everyone. Use it after adding a batch of milestones."),

    BACKFILL_MILESTONES_USER(JobGroup.MILESTONE, "Backfill one player's milestones",
            "Re-evaluates every milestone for a single player.",
            JobField.required("userId", JobFieldKind.USER, "Player", RunJobRequest::getUserId)),

    REGRANT_MILESTONE_REWARDS(JobGroup.MILESTONE, "Hand out one milestone's rewards",
            "Gives a milestone's items to everyone who already completed it, oldest completion first so serial"
                    + " numbers follow achievement order. Run this after attaching an item to a milestone players"
                    + " have already earned. Only users missing the item get it, so it is safe to re-run.",
            JobField.required("milestoneId", JobFieldKind.MILESTONE, "Milestone",
                    RunJobRequest::getMilestoneId)),

    REGRANT_MILESTONE_REWARDS_ALL(JobGroup.MILESTONE, "Hand out every milestone's rewards",
            "Sweeps every milestone that has items attached and gives each completer whatever they are missing,"
                    + " oldest completion first. Safe to re-run."),

    RESEQUENCE_ITEM_SERIALS(JobGroup.MISC, "Renumber an item's serials",
            "Rewrites serial numbers so they follow the order players actually earned the item, and moves"
                    + " Founder's onto the new lowest five. Only untradeable items whose copies all come from"
                    + " milestones or milestone sets can be renumbered, since nothing else has an achievement time"
                    + " to sort by. Run it after a reward backfill, never before.",
            JobField.optional("itemId", JobFieldKind.ITEM, "Item",
                    "Leave it off to sweep every eligible item.", RunJobRequest::getItemId)),

    REFRESH_COMPLEXITY_ESTIMATES(JobGroup.RECALCULATION, "Refresh complexity estimates",
            "Reruns both complexity scripts, the old BeatLeader accuracy formula and the current note accuracy one,"
                    + " over every ranked, qualified and queued difficulty and stores what each one says next to the"
                    + " complexity the map carries today. Nothing on the map changes. It takes about ten minutes"
                    + " because every difficulty is one call to BeatLeader."),

    REGENERATE_SONG_SUGGEST(JobGroup.MISC, "Regenerate song suggestions",
            "Rebuilds the song suggestion data from current scores.");

    private final JobGroup group;
    private final String label;
    private final String description;
    private final List<JobField> fields;

    JobType(JobGroup group, String label, String description, JobField... fields) {
        this.group = group;
        this.label = label;
        this.description = description;
        this.fields = List.of(fields);
    }
}
