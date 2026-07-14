# Missions

Daily and weekly objectives, tailored per player. Targets are built from each player's per-category skill, top AP, "AP for one gain" threshold, recent streaks, and the actual leaderboard on the picked map. Before touching any constant in here,

This only covers assignment and calibration. Mission completion lives in `MissionProgressService` and runs off score-submitted events.

---

## 1. The flow

It starts in `MissionAssignmentService`, triggered one of three ways:

- daily cron (4am) and weekly cron (Monday 4am) - wipes non-completed missions for the pool, re-rolls everyone
- login - fills missing daily/weekly if the user has none
- manual regen (admin/debug) - wipes and re-rolls a single user

For each user it builds a `MissionAssignmentContext` (active categories, per-category skills, ranked plays, rolling daily XP), then:

- daily: picks 2 missions. First slot is from "guaranteed-doable" templates if any exist, the rest are weighted by `MissionTemplate.weight`. Tries to avoid repeating categories.
- weekly: one mission per active category, with one randomly-chosen slot forced to extreme.

Picking a mission goes through `MissionBuilderService.pickAndBuild[ForCategory]`:

1. weighted template pick
2. category pick (skips overall, respects already-used)
3. dispatch to the right `build*` method based on `MissionType`
4. that method pulls math from `MissionTargetService`, `MissionSkillService`, `MissionCalibrationService`
5. returns a `UserMission`, or `null` and stashes a reason in `LAST_FAIL_REASON` (only logged at debug)

`MissionPoolCache` is loaded once per rollout (templates + poolable items + per-map WR cache) and shared. Don't query it from inside build methods.

---

## 2. What influences what

Common stuff every build pulls from the context:

- `skillByCategoryId` - per-category `UserCategorySkill` (skillLevel, topAp, rawApForOneGain)
- `rankedPlaysByCategoryId` - used by the cross-category skill lift to cool things down when the target category is under-played
- `rollingDailyXp` - only `XP_IN_WINDOW` cares

### Per mission type

- **PLAY_N_MAPS** - just a count, no map picked. Count picked by band. I have this disabled currently since not everyone will have the plugin at the start, but just gotta turn bool to true in DB
- **XP_IN_WINDOW** - `rollingDailyXp * bandMultiplier`, floored at 100.
- **ACC_ON_MAP / AP_ON_MAP** - full map-target pipeline (below). The acc variant converts to acc + score at the end; the AP variant uses the rawAp directly.
- **PB_SPECIFIC_MAP** - same pipeline, plus a `pbFreshnessBoost` XP bonus if the existing PB is recent.
- **PB_ABOVE_THRESHOLD** - percentile of the user's own scores (70/45/22/10 for easy/medium/hard/extreme) times a small shift (0.98/1.0/1.015/1.02), capped at 0.97 * topAp (with the skill-aware nerf described below for easy/medium/hard). Needs at least 2 qualifying scores or it fails.
- **SNIPE_PLAYER_ON_MAP** - two branches in `computeSnipeTarget` (has-score vs no-score). Candidate filter uses `snipeMaxSkillDistance` (5/8/12/18) to avoid asking you to snipe someone two tiers above.
- **STREAK_ON_MAP** - streak target built from the user's *per-complexity-band* representative streak, not one blended category number. The map is sampled first, its complexity resolves to a 3-wide half-open band (`[1,4) [4,7) [7,10) [10,13) [13,16)`), and we pull the user's top streaks **on maps in that band only** (`representativeUserStreakForComplexityBand`). That `bandAbility` feeds both the reference and the `bandAbility + 2` cap, so a hard map is measured against how the user streaks on hard maps - not inflated by their easy-map streaks. Reference = `0.6*avg + 0.4*max` of the map's own top-5 streaks; a ranked map always has imported scores, so `bandAbility` is only a defensive fallback if that leaderboard ever comes back empty. Final demand = `streakTargetFor(band)` clamped to `[3, bandAbility + 2]`. The `< 3` gate now runs **after** the pick on `bandAbility`, inside a `COMPUTE_MAP_RETRIES` resample loop: if the sampled map lands in a band the user can't streak 3 on, we resample rather than drop the mission. If the user has no streak data in the band at all, `bandAbility` falls back to their category-wide representative streak translated down by `complexityTranslationFactor` (so an easy-map streak isn't reused as-is on a hard map).
- **STREAK_N_IN_CATEGORY** - same streak logic, plus a count. Extreme + top-tier (skill >= 90) gets `+1` on the streak.
- **STREAK_SUM_N** - **event-only**, no build path (`buildMission` returns `failBuild("event-only-type")`, and a DB check pins the type to `pool = 'event'`). Targets come from `event_targets` like every event mission. Every submitted score adds its `streak115` to `progressCount` until it reaches `targetCount` - no per-map dedupe, and partials count, so attempts are included by design. It's the only type that sums a score field other than XP. If you ever want it in daily/weekly, drop `chk_mission_templates_streak_sum_event_only` and give it a real `build*` method - and be aware the additive count is grindable on one easy map, which is fine for a fixed-target event mission but would need a per-map cap for a calibrated pool.
- **COMEBACK_PB** - random old score (>1y), band derived from `weightedAp / maxWeightedAp` (so a comeback for a tiny historical play isn't "extreme").
- **SCORES_N** - always re-bands to easy or medium, XP scaled by `0.5 + 0.5 * count`.

### Bands

- Daily: random via `pickBand` - 30/40/25/5 (easy/medium/hard/extreme).
- Weekly: one slot forced to extreme, rest random.
- Map-picking missions can override the forced band if you already have a score on the picked map. The band gets blended with one derived from `weightedAp / maxWeightedAp` (60% assigned, 40% derived) via `blendBands`. This stops "you 99%'d this map, here's an extreme for +2 AP."

### The shared map-target pipeline

This runs for ACC_ON_MAP, AP_ON_MAP, PB_SPECIFIC_MAP and (in a slightly different shape) snipe:

| Step | What it does | Notes |
|---|---|---|
| `threshold = liftedThreshold(rawApForOneGain)` | starts from the user's "AP for one gain" | cross-category lift kicks in when the skill disparity is >= 10 |
| `skillAnchored = threshold * bandMultiplier` | what calibration "wants" before looking at the map | per-template multiplier from DB |
| `mapTarget = mapAwareTarget(...)` | walks the leaderboard for a comparable AP | uses user's skill and existing AP if any |
| `target = blendSkillAndMapTarget` | 30% skill / 70% map | map weight dominates so weak maps don't get inflated targets |
| `target = max(target, skillAnchored * skillFloorFraction(band))` | floor: don't go below skill anchor by too much | fractions 0.935 / 0.95 / 0.965 / 0.975 |
| `target = max(target, bandLiftedFloorAp(existing, complexity, band))` | only if user has a score | ensures PB missions actually beat the existing PB |
| `target = capExtremeAtTopAp(target, band, skill, skillLevel)` | hard ceiling vs topAp | factors 0.96 / 0.97 / 0.98 / 1.005; for skill < 70 the easy/medium/hard factors get a smoothstep nerf (up to ~7% at skill 0) so a 46-skill player doesn't get told to score 98% of their topAp on a hard map. Extreme is exempt — it should stay the only band that stretches past topAp |
| `target = capAtMapRealisticCeiling(...)` | skill-aware fraction of map WR | prevents "beat the WR" assignments |
| `target = applyLeaderboardDensityDampener(...)` | drop if the top of the leaderboard is too dense | only fires on hard/extreme |
| reject if `target <= existing` OR `target < minMeaningfulTarget` | sanity check | `minMeaningfulTarget` uses `0.70 * topAp` for hard/extreme - anything below that is busywork |

### Snipe specifically

Has-score branch: target = `max(bandLiftedFloorAp(userCurrentAp), skillFloor)`, capped + dampened.

No-score branch: blend skill-anchored (`threshold * snipeBandFraction`) with map target, cap. A small slack multiplier (1.0/1.01/1.03/1.04) is allowed on the candidate AP cap so we have a few candidates to pick from - but that inflated cap is then re-clamped by `capExtremeAtTopAp`, so for low-skill players where target already hits the ceiling, slack effectively turns off instead of letting a snipee 5% above their topAp sneak through.

Both branches feed `pickSnipeCandidate`, which ranks viable candidates by closeness to the target AP and picks one of the top 3.

The candidate query (`findSnipeCandidatesAboveBaselineWithSkill`) is **relevance-ordered by `ABS(s.ap - :targetAp)`** and then truncated to `SNIPE_CANDIDATE_LIMIT * 2`. That ordering is load-bearing, not cosmetic: the `LIMIT` decides which slice of the leaderboard Java ever sees, so the DB must rank by relevance to the target *before* the cut. Ordering it by anything else (`s.ap ASC` in particular) turns the limit into "hand me the weakest scores on the map" - the Java-side sort can only re-rank what survived the cut, so a strong player gets offered a snipe hundreds of AP below their level. This bit us once already; every map with more than `LIMIT * 2` eligible candidates is affected, which is most of them.

The two branches get **different** `minAp` floors, on purpose:

- **No-score branch**: `minAp = target * snipeFloorFraction(band)`. This is where snipes were wobbly - the target comes off the skill anchor blended with the map, and without a floor `pickSnipeCandidate` happily returns "closest to target" even when the closest thing on the map is 150 AP short. `slack` bounds how far above the target a candidate may sit; `snipeFloorFraction` bounds how far below.
- **Has-score branch**: `minAp = userCurrentAp`, and `apCap` stays exactly `target`. Do **not** apply `snipeFloorFraction` here. This branch was never the problem: it targets `bandLiftedFloorAp(userCurrentAp)`, so it already demands a real climb off your own PB rather than off the anchor (measured: 0.976 avg targetAp/topAp vs 0.872 on the no-score branch). The `userCurrentAp` floor is only a guard against a candidate whose raw `score` beats yours while its **AP is lower** - possible because `baseline` filters on modifier-multiplied `score` but snipes rank on AP.

The reason the has-score branch can't take a >1.0 floor: its `apCap` *is* the target, so a floor above 1.0 empties the window and every such snipe fails forever. That's a real trap - if you ever want a floor there, give it `snipeSlack` first.

---

## 3. Helper map + how to balance

### Where stuff lives

- `MissionAssignmentService` - cron, rollout, daily/weekly assignment, context build
- `MissionBuilderService` - per-type `build*` dispatch, snipe candidate selection, count/band rolls, item rolls
- `MissionTargetService` - all map/leaderboard-relative math (sample map, mapAwareTarget, caps, dampeners, band fractions)
- `MissionSkillService` - skill-relative math (cross-category lift, streak smoothing, age-adjusted AP, PB freshness)
- `MissionCalibrationService` - curve math (rawAp <-> acc, complexity range, lifted floor, XP reward)
- `MissionProgressService` - score-event evaluation, mission completion
- `MissionRolloverService` - deterministic seed + next-rollover instant
- `MissionTemplateService`, `MissionQueryService` - template CRUD + read APIs
- `MissionPoolCache`, `MissionAssignmentContext`, `MapPick` - pass-through records

### Knobs you'll actually want to tune (🤤)

- **`EXTREME_BOOST` (1.35)** in `MissionCalibrationService` - how much harder extreme is than hard. Past 1.5 and extreme becomes "impossible" rather than "hard."
- **per-template band multipliers** - live in the `mission_templates` DB rows, not hardcoded. Edit per-template in SQL.
- **`bandLiftedFloorAp` step + headroomFraction** - "improve PB by ~X% normalized" floor. Easy at 0.015 / 0.15 is already a real step; pushing it past 0.05 makes easy feel like hard.
- **`skillFloorFraction` (0.935/0.95/0.965/0.975)** - floor for map-blended target vs skill-anchored. Lower = more lazy maps slip through, higher = pipeline rejects too many candidates.
- **`snipeBandFraction` (0.93/0.95/0.97/0.985)** - mirrors `skillFloorFraction` by design. Keep them aligned.
- **`capExtremeAtTopAp` factors (0.96/0.97/0.98/1.005)** - extreme is intentionally allowed slightly above topAp (1.005). Drop below 1.0 and extreme = "match your current best", which is what we have hard for. There's also a smoothstep skill-aware nerf on easy/medium/hard (`applySkillAwareTopApNerf`, max 7% at skill 0, smoothly tapering to 0 at skill 70) so lower-half players don't get hard missions asking for ~98% of their topAp. The smoothstep means there's no cliff at 70 - drift is tiny up to skill 60 and only really bites under 50. Extreme is exempt by design; if you want lower-skill extreme tuned, tune `EXTREME_BOOST` or the per-template multiplier instead.
- **`mapWrFloorForBand` (0.80/0.86/0.90/0.94)** - rejects maps where the WR is too far below the user's topAp. Lower = more easy maps assigned to strong players.
- **`snipeFloorFraction(band)` (0.95/0.97/0.99/1.01)** - how far below the computed target a snipe candidate may sit before it's rejected (this is what the old notes called `minClimbFractionFor`, which was documented here for months but never actually existed in code). These are deliberately high because the target is anchored off `rawApForOneGain` - it's already a "one real gain" reference, not a stretch goal, so a snipe meaningfully below it isn't worth assigning. Extreme is **above 1.0** by design: it must beat the target, not match it. If you lower these, the "+2 AP snipe classified as extreme" bug comes back, and so does "snipe an 850 when you gain at 1040".
- **`snipeSlack(band)` (1.00/1.01/1.03/1.04)** - upper bound of the no-score window. Must stay strictly above `snipeFloorFraction` for the same band or that band becomes unassignable: the window is `[target * floorFraction, target * slack]`, and if the floor crosses the slack there is no AP a candidate could have.

  These knobs decide *quality*; the width they leave decides *how often a snipe gets assigned at all*. Measured on dev with 0.95/0.97/0.99/1.01, per-map viability is 8.5/11.8/13.1/13.2% and an 8-attempt build succeeds ~51/64/68/68% (vs ~53/65/70/71% at 0.93/0.95/0.96/0.97 - the floor is cheap; the **slack** cap is what drives the failure rate). A failed snipe falls through to another template rather than costing a slot, so failures cost snipe *frequency*, not missions. If snipes feel too rare, widen `snipeSlack` or raise `MAP_SAMPLE_ATTEMPTS`; don't drop the floor, that just reopens the "snipe an 850 when you gain at 1040" bug.
- **`pickBand` distribution (30/40/25/5)** - daily band mix. Bumping extreme past 10% gets visibly frustrating in the feedback channel.
- **`pickCount` `centerFrac` (0.17/0.50/0.83/1.00)** - where in `[min, max]` count missions center per band.
- **`representativeUserStreak` outlier rule** - if `max > median * 1.5`, switch to `median * bandMultiplier`. Without this, one fluke 115-streak permanently inflates streak missions.
- **`streakTargetFor` (0.5/0.7/0.9/1.0)** - streak target vs map reference. Hard/extreme get a `+1` for top-tier players.
- **streak complexity bands (`STREAK_COMPLEXITY_MIN/MAX/BAND_SIZE` = 1/15/3)** - width of the complexity buckets the per-band representative streak is drawn from. Wider bands = more of the user's scores qualify but coarser difficulty matching. Bands are half-open so fractional complexities never fall in a gap; keep it that way if you retune.
- **`STREAK_COMPLEXITY_BASE_FACTOR` (0.95) / `STREAK_COMPLEXITY_FACTOR_STEP` (0.15)** - the `complexityTranslationFactor` ramp (`0.95 → 0.35` across the 5 bands) that scales a category-wide streak down when the user has *no* streaks in the picked map's band. Only fires on that empty-band fallback - never double-applied to a band-matched streak.
- **`WR_DENSITY_THRESHOLD` (0.85), `CLIMB_GAP_SLOPE` (0.70)** - density dampener. Only fires on hard/extreme.
- **XP curves** - live in `curves` (POINT_LOOKUP) referenced by `mission_templates.xp_curve_id`. V61 nerfed mid/high-skill XP; re-tune the curve points, not constants.

### Things not to do

- If you find yourself writing `if band == X && type == Y`, there's a hole in the floor/ceiling pipeline. I think I've added enough decimals for a headache lol
- Don't strip the `LAST_FAIL_REASON` debug, its a good study within the debug logs when someone cries "I got no missions", that's probably our gateway to finding out why

### Smoke check when something feels off

For a known player, dump:

1. `skillLevel`, `topAp`, `rawApForOneGain` per category
2. For each active mission: `band`, `targetAp` / `targetAcc`, `mapDifficulty.complexity`, map WR
3. Check `targetAp / topAp` lands roughly in:
   - easy: 0.85-0.96 (skill <70 shaves a couple points off the top - sub-50 players closer to 0.83-0.93)
   - medium: 0.92-0.97 (same nerf - sub-50 sits closer to 0.90-0.95)
   - hard: 0.95-0.98 (same nerf - sub-50 sits closer to 0.93-0.96)
   - extreme: 0.97-1.005 (exempt from the skill nerf, same at any skill)

If extreme is consistently coming out below 0.95, the density dampener or `minMeaningfulTarget` is rejecting the strong candidates and falling back to a soft one - check the `LAST_FAIL_REASON` distribution before tweaking any constant

Okay thanks bye
