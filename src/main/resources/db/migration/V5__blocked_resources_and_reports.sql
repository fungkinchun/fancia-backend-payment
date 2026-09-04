create table if not exists blocked_resources (
    user_id uuid not null,
    resource_type varchar(32) not null
        check (resource_type in ('USER', 'POST', 'COMMENT', 'EVENT', 'INTEREST_GROUP', 'VENUE', 'TAG')),
    resource_id uuid not null,
    created_at timestamp(6),
    primary key (user_id, resource_type, resource_id),
    constraint fk_blocked_resources_user foreign key (user_id) references users (id)
);

create index if not exists idx_blocked_resources_user_type
    on blocked_resources (user_id, resource_type);

create table if not exists reports (
    id uuid not null,
    deleted boolean not null default false,
    created_at timestamp(6),
    created_by uuid,
    reporter_user_id uuid not null,
    target_type varchar(32) not null
        check (target_type in ('USER', 'POST', 'COMMENT', 'EVENT', 'INTEREST_GROUP', 'VENUE', 'TAG')),
    target_id uuid not null,
    reason varchar(32) not null
        check (reason in ('SPAM', 'HARASSMENT', 'HATE', 'SEXUAL', 'VIOLENCE', 'SCAM', 'OTHER')),
    details varchar(2000),
    status varchar(16) not null default 'OPEN'
        check (status in ('OPEN', 'REVIEWED', 'DISMISSED', 'ACTIONED')),
    primary key (id),
    constraint fk_reports_reporter foreign key (reporter_user_id) references users (id)
);

create index if not exists idx_reports_reporter on reports (reporter_user_id) where deleted = false;
create index if not exists idx_reports_target on reports (target_type, target_id) where deleted = false;

create table if not exists saved_resources (
    user_id uuid not null,
    resource_id uuid not null,
    created_at timestamp(6),
    primary key (user_id, resource_id)
);

create index if not exists idx_saved_resources_user_created
    on saved_resources (user_id, created_at desc);

drop table if exists user_blacklist_ids;
