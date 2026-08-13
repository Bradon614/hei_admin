-- HEI Admin domain schema.
-- Mirrors doc/api.yml, which is the single source of truth for the model.
-- Invariants that cannot be expressed here are enforced by the business
-- services and are pointed out in comments below.

create extension if not exists "pgcrypto";
-- Required by the exclusion constraint on student_group_assignment:
-- it mixes an equality operator on uuid with an overlap operator on daterange.
create extension if not exists btree_gist;

-- ---------------------------------------------------------------------------
-- Accounts
-- ---------------------------------------------------------------------------
create table app_user (
    id            uuid primary key default gen_random_uuid(),
    email         varchar(255) not null unique,
    password_hash varchar(255) not null,
    role          varchar(20)  not null check (role in ('STUDENT', 'TEACHER', 'ADMIN')),
    created_at    timestamptz  not null default now()
);

-- ---------------------------------------------------------------------------
-- Academic structure
-- ---------------------------------------------------------------------------
create table promotion (
    id         uuid primary key default gen_random_uuid(),
    ref        varchar(5)   not null unique,
    name       varchar(100) not null,
    start_year int          not null,
    end_year   int          not null,
    constraint promotion_years_ck check (end_year > start_year)
);

create table track (
    id   uuid primary key default gen_random_uuid(),
    code varchar(10)  not null unique,
    name varchar(100) not null
);

-- Tracks opened by a promotion, from the first non common core semester on.
create table promotion_track (
    promotion_id uuid not null references promotion (id) on delete cascade,
    track_id     uuid not null references track (id) on delete restrict,
    primary key (promotion_id, track_id)
);

create table semester (
    id               uuid primary key default gen_random_uuid(),
    ref              varchar(5) not null unique check (ref in ('S1', 'S2', 'S3', 'S4', 'S5', 'S6')),
    sem_order        smallint   not null unique check (sem_order between 1 and 6),
    year_number      smallint   not null check (year_number between 1 and 3),
    required_credits smallint   not null default 30 check (required_credits > 0),
    -- true for S1-S3 (common core), false for S4-S6 (a track applies).
    -- Reference data, never a constant hardcoded in the business code.
    common_core      boolean    not null
);

create table student_group (
    id           uuid primary key default gen_random_uuid(),
    ref          varchar(10) not null,
    promotion_id uuid        not null references promotion (id) on delete restrict,
    -- null = common core group, otherwise the group belongs to that track.
    -- Only used to check assignment consistency, never to derive a student
    -- curriculum or results: those come from student_track_choice alone.
    track_id     uuid,
    unique (promotion_id, ref),
    -- A track-bearing group must carry a track its promotion actually opens.
    -- MATCH SIMPLE: a null track_id satisfies the constraint, which is exactly
    -- what common core groups need.
    constraint student_group_promotion_track_fk
        foreign key (promotion_id, track_id)
            references promotion_track (promotion_id, track_id) on delete restrict
);

create index student_group_promotion_idx on student_group (promotion_id);

-- ---------------------------------------------------------------------------
-- People
-- ---------------------------------------------------------------------------
-- No track_id here on purpose: a student has no track until S4, so a track is
-- never a permanent property of a student. See student_track_choice.
-- No group_id either: membership is historised in student_group_assignment.
-- No graduated flag either: graduation is recomputed from S1 to S6.
create table student (
    id            uuid primary key default gen_random_uuid(),
    ref           varchar(20)  not null unique,
    first_name    varchar(100) not null,
    last_name     varchar(100) not null,
    email         varchar(255) not null unique,
    birth_date    date,
    entrance_date date         not null,
    status        varchar(20)  not null default 'ACTIVE'
        check (status in ('ACTIVE', 'SUSPENDED', 'LEFT')),
    promotion_id  uuid         not null references promotion (id) on delete restrict,
    user_id       uuid         not null unique references app_user (id) on delete restrict,
    created_at    timestamptz  not null default now()
);

create index student_promotion_idx on student (promotion_id);
create index student_name_idx on student (last_name, first_name);

create table teacher (
    id         uuid primary key default gen_random_uuid(),
    ref        varchar(20)  not null unique,
    first_name varchar(100) not null,
    last_name  varchar(100) not null,
    email      varchar(255) not null unique,
    user_id    uuid         not null unique references app_user (id) on delete restrict,
    created_at timestamptz  not null default now()
);

-- ---------------------------------------------------------------------------
-- Group membership history
-- ---------------------------------------------------------------------------
-- A student may change group at any moment, several times within the same year
-- or the same semester. No academic year appears in the key: only dates matter.
create table student_group_assignment (
    id         uuid        primary key default gen_random_uuid(),
    student_id uuid        not null references student (id) on delete cascade,
    group_id   uuid        not null references student_group (id) on delete restrict,
    start_date date        not null,
    end_date   date,
    reason     varchar(255),
    created_at timestamptz not null default now(),

    constraint sga_dates_ck check (end_date is null or end_date >= start_date),

    -- Forbids two simultaneous assignments for the same student.
    -- Inclusive bounds: [01/09, 14/11] and [15/11, ...] do not overlap, while
    -- [01/09, 30/11] and [15/11, 31/12] do.
    constraint sga_no_overlap exclude using gist (
        student_id with =,
        daterange(start_date, coalesce(end_date, date 'infinity'), '[]') with &&
        )
);

create index sga_student_period_idx on student_group_assignment (student_id, start_date, end_date);
create index sga_group_idx on student_group_assignment (group_id);

-- Business rules enforced by the service layer, not expressible here:
--  - the group must belong to the promotion of the student;
--  - R1: assigning to a track-bearing group requires an existing
--    student_track_choice for the same track;
--  - R2: once a choice exists, any assignment created afterwards must target a
--    group of that track.

-- ---------------------------------------------------------------------------
-- Track choice
-- ---------------------------------------------------------------------------
-- Deferred effect model: one row covers the nominal case (EL from S4 covers S4,
-- S5 and S6). A reorientation is a further row with a higher semester, so no
-- end column and no update of the existing row.
create table student_track_choice (
    id               uuid        primary key default gen_random_uuid(),
    student_id       uuid        not null references student (id) on delete cascade,
    track_id         uuid        not null references track (id) on delete restrict,
    from_semester_id uuid        not null references semester (id) on delete restrict,
    decided_at       timestamptz not null default now(),
    reason           varchar(255),
    unique (student_id, from_semester_id)
);

create index stc_student_idx on student_track_choice (student_id);

-- Business rules enforced by the service layer:
--  - from_semester.common_core must be false;
--  - the first choice takes effect on the first non common core semester;
--  - the chosen track must be opened by the promotion of the student;
--  - a non common core semester left uncovered yields TRACK_NOT_SELECTED,
--    never a silent absence of track.

-- ---------------------------------------------------------------------------
-- Curriculum
-- ---------------------------------------------------------------------------
-- A course belongs to exactly one semester, which makes the semester of a grade
-- derivable through grade -> exam -> course -> semester.
create table course (
    id          uuid         primary key default gen_random_uuid(),
    ref         varchar(20)  not null unique,
    title       varchar(200) not null,
    credits     smallint     not null check (credits > 0),
    semester_id uuid         not null references semester (id) on delete restrict,
    -- null  -> common course: the whole common core in S1-S3, and the courses
    --          shared by both tracks in S4-S6
    -- EL/TN -> course specific to that track, invisible to the other one
    track_id    uuid references track (id) on delete restrict
);

create index course_semester_idx on course (semester_id);
create index course_track_idx on course (track_id);

-- Business rule enforced by the service layer: a course of a common_core
-- semester must have a null track_id.

-- Ternary relation course x teacher x group. Three binary join tables could not
-- tell who teaches which group. A missing (course, group) row means the course
-- is not given to that group.
create table teaching_assignment (
    id         uuid        primary key default gen_random_uuid(),
    course_id  uuid        not null references course (id) on delete cascade,
    teacher_id uuid        not null references teacher (id) on delete restrict,
    group_id   uuid        not null references student_group (id) on delete cascade,
    created_at timestamptz not null default now(),
    unique (course_id, teacher_id, group_id)
);

create index ta_teacher_idx on teaching_assignment (teacher_id);
create index ta_course_group_idx on teaching_assignment (course_id, group_id);

create table exam (
    id          uuid         primary key default gen_random_uuid(),
    course_id   uuid         not null references course (id) on delete cascade,
    title       varchar(100) not null,
    date_exam   timestamptz  not null,
    coefficient numeric(4, 2) not null check (coefficient > 0),
    unique (course_id, title)
);

create index exam_course_idx on exam (course_id);

-- ---------------------------------------------------------------------------
-- Grades
-- ---------------------------------------------------------------------------
-- Attached to the student and the exam, never to a group: this is the
-- structural guarantee that a group change cannot make a grade disappear.
-- No semester either, it is derivable through the exam.
create table grade (
    id         uuid          primary key default gen_random_uuid(),
    student_id uuid          not null references student (id) on delete cascade,
    exam_id    uuid          not null references exam (id) on delete cascade,
    value      numeric(4, 2) not null check (value >= 0 and value <= 20),
    created_at timestamptz   not null default now(),
    updated_at timestamptz   not null default now(),
    unique (student_id, exam_id)
);

create index grade_student_idx on grade (student_id);
create index grade_exam_idx on grade (exam_id);

-- One row per entry or update. Never updated, never deleted.
create table grade_history (
    id          uuid          primary key default gen_random_uuid(),
    grade_id    uuid          not null references grade (id) on delete cascade,
    old_value   numeric(4, 2) check (old_value is null or (old_value >= 0 and old_value <= 20)),
    new_value   numeric(4, 2) not null check (new_value >= 0 and new_value <= 20),
    reason_type varchar(20)   not null
        check (reason_type in ('CREATION', 'CLAIM', 'INPUT_ERROR', 'CORRECTION', 'OTHER')),
    -- A modification without a reason must be impossible.
    reason      text          not null check (length(btrim(reason)) > 0),
    changed_by  uuid          not null references app_user (id) on delete restrict,
    changed_at  timestamptz   not null default now(),
    constraint grade_history_value_changed_ck check (old_value is distinct from new_value)
);

create index grade_history_grade_idx on grade_history (grade_id, changed_at desc);

-- ---------------------------------------------------------------------------
-- Transcript (PDF + S3 + asynchronous email)
-- ---------------------------------------------------------------------------
-- Tracks the lifecycle of a request, which makes the asynchronous processing
-- observable and testable.
create table transcript_request (
    id            uuid        primary key default gen_random_uuid(),
    student_id    uuid        not null references student (id) on delete cascade,
    -- null = full S1 to S6 transcript
    semester_id   uuid references semester (id) on delete restrict,
    status        varchar(20) not null default 'PENDING'
        check (status in ('PENDING', 'GENERATED', 'SENT', 'FAILED')),
    s3_key        varchar(500),
    file_url      text,
    requested_by  uuid        not null references app_user (id) on delete restrict,
    requested_at  timestamptz not null default now(),
    generated_at  timestamptz,
    sent_at       timestamptz,
    error_message text
);

create index transcript_request_student_idx on transcript_request (student_id, requested_at desc);
