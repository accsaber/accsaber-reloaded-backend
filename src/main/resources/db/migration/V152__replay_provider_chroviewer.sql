UPDATE user_settings
SET value      = '"chroviewer"'::jsonb,
    updated_at = now()
WHERE key IN ('appearance.primaryReplayService', 'appearance.fallbackReplayService')
  AND value = '"scoresaber"'::jsonb;
