package com.accsaber.backend.model.entity.item;

public enum ItemRarity {
    common,
    uncommon,
    rare,
    epic,
    legendary,
    mythic;

    public static final String RANK_SQL_CASES = " WHEN 'mythic' THEN 6 WHEN 'legendary' THEN 5 WHEN 'epic' THEN 4"
            + " WHEN 'rare' THEN 3 WHEN 'uncommon' THEN 2 WHEN 'common' THEN 1 ELSE 0 END";
}
