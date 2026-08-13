-- Fixed academic reference data.
--
-- The curriculum spans three years, two semesters per year, 30 credits each,
-- 180 credits in total.
--
-- common_core carries the whole common core / EL-TN rule as DATA: the rank of
-- the semester from which a track must be chosen appears nowhere in the
-- business code. Moving the choice to another semester, or opening a third
-- track, is an update here and nothing else.
--
-- Note that the common core boundary falls in the middle of year 2: S3 and S4
-- are both year 2, yet S3 is common core and S4 is not.

insert into semester (ref, sem_order, year_number, required_credits, common_core)
values ('S1', 1, 1, 30, true),
       ('S2', 2, 1, 30, true),
       ('S3', 3, 2, 30, true),
       ('S4', 4, 2, 30, false),
       ('S5', 5, 3, 30, false),
       ('S6', 6, 3, 30, false)
on conflict (ref) do nothing;

-- The two HEI tracks. A track only applies from the first non common core
-- semester on, and is never attached directly to a student: see
-- student_track_choice.
insert into track (code, name)
values ('EL', 'Software Ecosystem'),
       ('TN', 'Digital Transformation')
on conflict (code) do nothing;
