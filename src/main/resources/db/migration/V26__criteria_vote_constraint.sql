ALTER TABLE staff_map_votes DROP CONSTRAINT staff_map_votes_criteria_vote_check;
ALTER TABLE staff_map_votes ADD CONSTRAINT staff_map_votes_criteria_vote_check
    CHECK (criteria_vote = ANY (ARRAY['upvote'::text, 'downvote'::text, 'neutral'::text]));

UPDATE staff_map_votes SET criteria_vote = 'upvote' WHERE criteria_vote = 'pass';
UPDATE staff_map_votes SET criteria_vote = 'downvote' WHERE criteria_vote = 'fail';
