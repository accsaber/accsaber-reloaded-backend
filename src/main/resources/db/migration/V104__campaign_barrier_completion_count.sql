ALTER TABLE campaign_difficulties
    DROP CONSTRAINT IF EXISTS campaign_difficulties_barrier_condition_type_check;

ALTER TABLE campaign_difficulties
    ADD CONSTRAINT campaign_difficulties_barrier_condition_type_check
        CHECK (barrier_condition_type IN ('AVERAGE_ACC', 'AVERAGE_AP', 'AP_MAX', 'ACC_MAX',
            'STREAK_115_AVERAGE', 'STREAK_115_MAX', 'FC', 'AVERAGE_RANK', 'MAX_RANK',
            'COMPLETION_COUNT'));
