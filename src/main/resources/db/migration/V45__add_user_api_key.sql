-- Credential backing the BearerAuth scheme declared in doc/api.yml.
--
-- The API is stateless and has no login endpoint: the value sent in
-- "Authorization: Bearer <api_key>" resolves the caller directly. password_hash
-- stays in place for a possible future login flow but is unused for now.
--
-- The default is only there so the column can be added on a table that already
-- holds rows; it is dropped right after so that every account is given an
-- explicit key by the application.
alter table app_user
    add column api_key varchar(255) not null default gen_random_uuid()::text;

alter table app_user
    alter column api_key drop default;

alter table app_user
    add constraint app_user_api_key_uq unique (api_key);
