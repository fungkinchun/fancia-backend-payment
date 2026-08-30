alter table posts
    add column kind varchar(16) not null default 'TEXT';

alter table posts
    add constraint posts_kind_check check (kind in ('TEXT', 'POLL'));

alter table posts
    add column status varchar(16) not null default 'VISIBLE';

alter table posts
    add column expired_at timestamp(6);

alter table posts
    add constraint posts_status_check check (status in ('VISIBLE', 'FEATURED', 'PINNED', 'READ_ONLY', 'HIDDEN'));

alter table posts
    drop column is_featured;

alter table posts
    drop column is_pinned;

create index posts_expired_at_status_idx on posts (expired_at, status)
    where deleted = false and expired_at is not null;

create table post_polls (
    post_id uuid not null,
    allow_multiple boolean not null default false,
    closes_at timestamp(6),
    primary key (post_id),
    constraint fk_post_polls_post foreign key (post_id) references posts (id)
);

create table post_poll_options (
    deleted boolean not null default false,
    sort_order integer not null,
    created_at timestamp(6),
    created_by uuid,
    id uuid not null,
    post_id uuid not null,
    label varchar(255) not null,
    primary key (id),
    constraint fk_post_poll_options_poll foreign key (post_id) references post_polls (post_id)
);

create index post_poll_options_post_id_idx on post_poll_options (post_id);

create table post_poll_votes (
    voted_at timestamp(6),
    post_id uuid not null,
    option_id uuid not null,
    user_id uuid not null,
    primary key (post_id, option_id, user_id),
    constraint fk_post_poll_votes_poll foreign key (post_id) references post_polls (post_id),
    constraint fk_post_poll_votes_option foreign key (option_id) references post_poll_options (id)
);

create index post_poll_votes_post_user_idx on post_poll_votes (post_id, user_id);

alter table events
    add column event_type varchar(32) not null default 'REGULAR';

alter table events
    add constraint events_event_type_check check (event_type in ('REGULAR', 'SPONTANEOUS'));

create index events_event_type_idx on events (event_type) where deleted = false;
