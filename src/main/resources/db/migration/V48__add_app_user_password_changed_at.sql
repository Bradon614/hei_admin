alter table app_user
    add column password_changed_at timestamptz not null default date_trunc('second', now());

update app_user
set password_changed_at = date_trunc('second', created_at);
