-- Human-readable public URL slugs for events, venues, interest groups, and users.
-- Event/venue/group slugs are backfilled with a short id suffix for uniqueness.
-- User handles stay null until chosen in onboarding or settings.

alter table events add column slug varchar(255);

update events
set slug = trim(both '-' from lower(regexp_replace(
        coalesce(nullif(trim(name), ''), 'event'),
        '[^a-zA-Z0-9]+',
        '-',
        'g'
    )))
    || '-' || left(replace(id::text, '-', ''), 8)
where slug is null;

alter table events alter column slug set not null;

create unique index uk_events_slug on events (slug) where deleted = false;

alter table venues add column slug varchar(255);

update venues
set slug = trim(both '-' from lower(regexp_replace(
        coalesce(nullif(trim(name), ''), 'venue')
            || case
                   when city is not null and trim(city) <> '' then '-' || trim(city)
                   else ''
               end,
        '[^a-zA-Z0-9]+',
        '-',
        'g'
    )))
    || '-' || left(replace(id::text, '-', ''), 8)
where slug is null;

alter table venues alter column slug set not null;

create unique index uk_venues_slug on venues (slug) where deleted = false;

alter table interest_groups add column slug varchar(255);

update interest_groups
set slug = trim(both '-' from lower(regexp_replace(
        coalesce(nullif(trim(name), ''), 'group'),
        '[^a-zA-Z0-9]+',
        '-',
        'g'
    )))
    || '-' || left(replace(id::text, '-', ''), 8)
where slug is null;

alter table interest_groups alter column slug set not null;

create unique index uk_interest_groups_slug on interest_groups (slug) where deleted = false;

alter table users add column slug varchar(255);
alter table users add column slug_changed_at timestamp(6);

create unique index uk_users_slug on users (slug) where deleted = false and slug is not null;

create table user_slug_history (
    slug varchar(255) not null,
    user_id uuid not null,
    created_at timestamp(6) not null default now(),
    constraint user_slug_history_pkey primary key (slug),
    constraint fk_user_slug_history_user foreign key (user_id) references users (id)
);

create index idx_user_slug_history_user_id on user_slug_history (user_id);
