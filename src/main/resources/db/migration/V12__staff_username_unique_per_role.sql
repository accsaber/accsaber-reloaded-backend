DROP INDEX IF EXISTS unique_staff_users_username_active;
CREATE UNIQUE INDEX unique_staff_users_username_role_active
    ON staff_users(username, role) WHERE active = true AND username IS NOT NULL;
