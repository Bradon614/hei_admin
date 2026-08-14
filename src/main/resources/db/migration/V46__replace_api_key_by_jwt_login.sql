-- Authentication moves from a bearer API key to a signed JWT issued by POST /auth/login.
--
-- A JWT is validated by its signature, not by a database lookup, so the key that used to be
-- looked up at request time has no reader left. Keeping it would leave a second, unused, way to
-- authenticate every account. password_hash was already NOT NULL and switches from a placeholder
-- constant to a real BCrypt hash in application code; no schema change is needed for it.
alter table app_user
    drop constraint app_user_api_key_uq;

alter table app_user
    drop column api_key;
