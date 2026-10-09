ALTER TABLE clan_members DROP CONSTRAINT chk_clan_members_leave_reason;

ALTER TABLE clan_members ADD CONSTRAINT chk_clan_members_leave_reason
    CHECK (leave_reason IN ('left', 'kicked', 'disbanded', 'banned', 'merged', 'waived'));
