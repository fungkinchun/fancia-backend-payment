-- Consolidated schema baseline (former V2–V18). Fresh installs only; do not alter checksums on existing DBs without a Flyway reset.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

create table "authorization_consents" (authorities varchar(1000), "client_id" varchar(255), principal_name varchar(255) not null, registered_client_id varchar(255) not null, primary key (principal_name, registered_client_id));
create table "authorizations" (access_token_expires_at timestamp(6) with time zone, access_token_issued_at timestamp(6) with time zone, authorization_code_expires_at timestamp(6) with time zone, authorization_code_issued_at timestamp(6) with time zone, device_code_expires_at timestamp(6) with time zone, device_code_issued_at timestamp(6) with time zone, oidc_id_token_expires_at timestamp(6) with time zone, oidc_id_token_issued_at timestamp(6) with time zone, refresh_token_expires_at timestamp(6) with time zone, refresh_token_issued_at timestamp(6) with time zone, user_code_expires_at timestamp(6) with time zone, user_code_issued_at timestamp(6) with time zone, state varchar(500), access_token_scopes varchar(1000), authorized_scopes varchar(1000), access_token_metadata varchar(2000), device_code_metadata varchar(2000), oidc_id_token_claims varchar(2000), oidc_id_token_metadata varchar(2000), refresh_token_metadata varchar(2000), user_code_metadata varchar(2000), access_token_value varchar(32600), attributes varchar(32600), authorization_code_value varchar(32600), device_code_value varchar(32600), oidc_id_token_value varchar(32600), refresh_token_value varchar(32600), user_code_value varchar(32600), access_token_type varchar(255), authorization_code_metadata varchar(255), authorization_grant_type varchar(255), "client_id" varchar(255), id varchar(255) not null, principal_name varchar(255), primary key (id));
create table "clients" (client_id_issued_at timestamp(6) with time zone, client_secret_expires_at timestamp(6) with time zone, authorization_grant_types varchar(1000), client_authentication_methods varchar(1000), post_logout_redirect_uris varchar(1000), redirect_uris varchar(1000), scopes varchar(1000), client_settings varchar(2000), token_settings varchar(2000), client_id varchar(255) unique, client_name varchar(255), client_secret varchar(255), id varchar(255) not null, primary key (id));
create table password_reset_tokens (deleted boolean not null, email_sent boolean not null, created_at timestamp(6), expires_at timestamp(6), created_by uuid, id uuid not null, "user_id" uuid, token varchar(255), primary key (id));
comment on column password_reset_tokens.deleted is 'Soft-delete indicator';
create table privilege (deleted boolean not null, created_at timestamp(6), created_by uuid, id uuid not null, name varchar(255) not null unique, primary key (id));
comment on column privilege.deleted is 'Soft-delete indicator';
create table user_connected_accounts (deleted boolean not null, connected_at timestamp(6), created_at timestamp(6), created_by uuid, id uuid not null, user_id uuid, provider varchar(255), provider_id varchar(255), primary key (id));
comment on column user_connected_accounts.deleted is 'Soft-delete indicator';
create table uploaded_file (deleted boolean not null, created_at timestamp(6), size bigint, uploaded_at timestamp(6), created_by uuid, id uuid not null, "user_id" uuid, extension varchar(255), original_file_name varchar(255), url varchar(255), primary key (id));
comment on column uploaded_file.deleted is 'Soft-delete indicator';
create table user_links (user_id uuid not null, type varchar(50) not null check ((type in ('WEBSITE','INSTAGRAM','FACEBOOK','TWITTER','LINKEDIN','YOUTUBE','TIKTOK'))), url varchar(255) not null, primary key (user_id, type, url));
create table user_privileges (privilege_id uuid not null, user_id uuid not null, primary key (privilege_id, user_id));
create table "users" (
    deleted boolean not null,
    status varchar(16) not null default 'REGISTERED' check (status in ('REGISTERED', 'ACTIVE', 'INACTIVE')),
    created_at timestamp(6),
    created_by uuid,
    id uuid not null,
    email varchar(255),
    first_name varchar(255),
    last_name varchar(255),
    password varchar(255),
    profile_image_url varchar(255),
    role varchar(255) check ((role in ('USER','ADMIN'))),
    bio varchar(4000),
    location_label varchar(500),
    birth_date date,
    gender varchar(1) check (gender in ('M', 'F')),
    visibility varchar(16) not null default 'PUBLIC' check (visibility in ('PUBLIC', 'PRIVATE')),
    premium_active boolean not null default false,
    premium_expires_at timestamp without time zone,
    primary key (id)
);
comment on column "users".deleted is 'Soft-delete indicator';
create table tags (deleted boolean not null, created_at timestamp(6), created_by uuid, id uuid not null, name varchar(50) not null, type varchar(32) not null check (type in ('INTEREST', 'SKILL', 'TOPIC', 'SYSTEM')), primary key (id), unique (name, type));
comment on column tags.deleted is 'Soft-delete indicator';
create table user_tags (user_id uuid not null, tag_id uuid not null, primary key (user_id, tag_id));
create table user_settings (user_id uuid primary key, privacy jsonb not null default '{}', notifications jsonb not null default '{}');
create table verification_codes (deleted boolean not null, email_sent boolean not null, created_at timestamp(6), created_by uuid, id uuid not null, "user_id" uuid unique, code varchar(255), primary key (id));
comment on column verification_codes.deleted is 'Soft-delete indicator';
create table user_blacklist_ids (
    user_id uuid not null,
    blacklisted_id uuid not null,
    primary key (user_id, blacklisted_id),
    constraint fk_user_blacklist_ids_user foreign key (user_id) references "users" (id)
);
create index user_blacklist_ids_blacklisted_id_idx on user_blacklist_ids (blacklisted_id);

alter table if exists "authorization_consents" add constraint FK7u3rrcx79xyss37m2551mpx2p foreign key ("client_id") references "clients";
alter table if exists "authorizations" add constraint FK4ehcr3h1eun20h36is62nal65 foreign key ("client_id") references "clients";
alter table if exists password_reset_tokens add constraint FKrjxrqd0dudi212f0469dojcod foreign key ("user_id") references "users";
alter table if exists uploaded_file add constraint FKqhosch3tnq7i2it0mea57ts4m foreign key ("user_id") references "users";
alter table if exists user_connected_accounts add constraint FKnnce63ye8wbmdskoeco5ku43d foreign key (user_id) references "users";
alter table if exists user_links add constraint FK4wc3hhebo87m149hnxkxxmfvm foreign key (user_id) references "users";
alter table if exists user_tags add constraint FK_user_tags_user foreign key (user_id) references "users";
alter table if exists user_settings add constraint FK_user_settings_user foreign key (user_id) references "users";
alter table if exists user_privileges add constraint FK6rrv8daxxrco69tdpfu3a29le foreign key (privilege_id) references privilege;
alter table if exists user_privileges add constraint FKobuc3eaoytxqaj534be5b7xqs foreign key (user_id) references "users";
alter table if exists verification_codes add constraint FK2c664upaiv1f6h7e5ueyy1ae3 foreign key ("user_id") references "users";

create table comment_likes (liked_at timestamp(6), comment_id uuid not null, user_id uuid not null, primary key (comment_id, user_id));
create table comments (deleted boolean not null, created_at timestamp(6), author_user_id uuid not null, created_by uuid, id uuid not null, resource_id uuid, target_id uuid not null, body varchar(4000) not null, primary key (id));
comment on column comments.deleted is 'Soft-delete indicator';
create table post_likes (liked_at timestamp(6), post_id uuid not null, user_id uuid not null, primary key (post_id, user_id));
create table post_media (deleted boolean not null, sort_order integer not null, created_at timestamp(6), created_by uuid, id uuid not null, media_type varchar(16) not null check ((media_type in ('IMAGE','VIDEO'))), post_id uuid not null, object_key varchar(1024) not null, primary key (id));
comment on column post_media.deleted is 'Soft-delete indicator';
create table posts (deleted boolean not null, is_featured boolean not null, is_pinned boolean not null, created_at timestamp(6), author_user_id uuid not null, created_by uuid, id uuid not null, target_id uuid not null, body varchar(4000), primary key (id));
comment on column posts.deleted is 'Soft-delete indicator';
alter table if exists comment_likes add constraint FK3wa5u7bs1p1o9hmavtgdgk1go foreign key (comment_id) references comments;
alter table if exists post_likes add constraint FKa5wxsgl4doibhbed9gm7ikie2 foreign key (post_id) references posts;
alter table if exists post_media add constraint FK1urcum9dtf0vgul7k405f4r2d foreign key (post_id) references posts;

create table interest_groups (deleted boolean not null, created_at timestamp(6), created_by uuid, id uuid not null, description varchar(4000) not null, name varchar(255) not null, primary key (id));
comment on column interest_groups.deleted is 'Soft-delete indicator';
create table interest_group_links (interest_group_id uuid not null, type varchar(50) not null check ((type in ('WEBSITE','INSTAGRAM','FACEBOOK','TWITTER','LINKEDIN','YOUTUBE','TIKTOK'))), url varchar(255) not null, primary key (interest_group_id, type, url));
create table interest_group_membership (joined_at timestamp(6), interest_group_id uuid not null, user_id uuid not null, role varchar(255) check ((role in ('ADMIN','MEMBER'))), status varchar(255) check ((status in ('ACCEPTED','PENDING','DENIED','WITHDREW','BANNED'))), primary key (interest_group_id, user_id));
create table interest_group_tags (interest_group_id uuid not null, tag_id uuid not null, primary key (interest_group_id, tag_id));
alter table if exists interest_group_links add constraint FK3519psi1d5n0prhs7ectxqyfy foreign key (interest_group_id) references interest_groups;
alter table if exists interest_group_membership add constraint FK969x3gmh9kq16vevdr74h0t3g foreign key (interest_group_id) references interest_groups;
alter table if exists interest_group_tags add constraint FKcbscsmlvmrmdqc0ih8c6dlgkk foreign key (interest_group_id) references interest_groups;

create table venues (deleted boolean not null, latitude double precision, longitude double precision, created_at timestamp(6), created_by uuid, id uuid not null, postcode varchar(50), country varchar(100), address_line varchar(500), location_label varchar(500), description varchar(4000) not null, city varchar(255), name varchar(255) not null, place_id varchar(255), primary key (id));
comment on column venues.deleted is 'Soft-delete indicator';
create table venue_links (venue_id uuid not null, type varchar(50) not null check ((type in ('WEBSITE','INSTAGRAM','FACEBOOK','TWITTER','LINKEDIN','YOUTUBE','TIKTOK','ZOOM','TEAMS','GOOGLE_MEET'))), url varchar(255) not null, primary key (venue_id, type, url));
create table venue_staff (joined_at timestamp(6), venue_id uuid not null, user_id uuid not null, role varchar(255) check ((role in ('ADMIN','MEMBER'))), status varchar(255) check ((status in ('ACCEPTED','PENDING','DENIED','WITHDREW','BANNED'))), primary key (venue_id, user_id));
create table venue_tags (venue_id uuid not null, tag_id uuid not null, primary key (venue_id, tag_id));
alter table if exists venue_links add constraint FKvenue_links_venue foreign key (venue_id) references venues;
alter table if exists venue_staff add constraint FKvenue_staff_venue foreign key (venue_id) references venues;
alter table if exists venue_tags add constraint FKvenue_tags_venue foreign key (venue_id) references venues;
create index venues_geo_idx on venues using gist (
    geography(st_setsrid(st_makepoint(longitude, latitude), 4326))
) where latitude is not null and longitude is not null and deleted = false;

create table events (
    deleted boolean not null,
    created_at timestamp(6),
    end_time timestamp(6),
    start_time timestamp(6),
    created_by uuid,
    id uuid not null,
    location_kind varchar(32) check (location_kind in ('ADDRESS','VENUE','ONLINE')),
    venue_id uuid,
    location_label varchar(500),
    place_id varchar(255),
    latitude double precision,
    longitude double precision,
    address_line varchar(500),
    city varchar(255),
    postcode varchar(50),
    country varchar(100),
    description varchar(4000) not null,
    name varchar(255) not null,
    visibility varchar(255) not null check ((visibility in ('PUBLIC','GROUP','PRIVATE'))),
    recurrence_frequency varchar(32) not null default 'NONE',
    recurrence_days_mask smallint not null default 0,
    recurrence_paused_until timestamp(6) with time zone,
    approval_required boolean not null default true,
    primary key (id)
);
comment on column events.deleted is 'Soft-delete indicator';
create table event_interest_groups (event_id uuid not null, event_interest_groups uuid);
create table event_venues (event_id uuid not null, event_venues uuid);
create table event_tags (event_id uuid not null, tag_id uuid not null, primary key (event_id, tag_id));
create table event_links (event_id uuid not null, type varchar(50) not null check ((type in ('WEBSITE','INSTAGRAM','FACEBOOK','TWITTER','LINKEDIN','YOUTUBE','TIKTOK','ZOOM','TEAMS','GOOGLE_MEET'))), url varchar(255) not null, primary key (event_id, type, url));
create table event_occurrences (
    deleted boolean not null default false,
    created_at timestamp(6),
    created_by uuid,
    id uuid not null,
    event_id uuid not null,
    start_time timestamp(6) not null,
    end_time timestamp(6) not null,
    status varchar(32) not null default 'SCHEDULED'
        check (status in ('SCHEDULED', 'CANCELLED')),
    primary key (id),
    constraint uk_event_occurrences_event_start unique (event_id, start_time),
    constraint fk_event_occurrences_event foreign key (event_id) references events (id)
);
create index event_occurrences_event_id_idx on event_occurrences (event_id);
create index event_occurrences_start_time_idx on event_occurrences (start_time);
create table event_participants (
    occurrence_id uuid not null,
    user_id uuid not null,
    role varchar(255) check ((role in ('HOST','COHOST','GUEST'))),
    primary key (occurrence_id, user_id),
    constraint fk_event_participants_occurrence foreign key (occurrence_id) references event_occurrences (id)
);
create table reservations (
    guests integer not null,
    occurrence_id uuid not null,
    user_id uuid not null,
    payload varchar(4000),
    status varchar(255) check ((status in ('PENDING','ACCEPTED','WHITELIST','DENIED','WITHDREW'))),
    primary key (occurrence_id, user_id),
    constraint fk_reservations_occurrence foreign key (occurrence_id) references event_occurrences (id)
);
alter table if exists event_interest_groups add constraint FK9pyxt3n5c0gtivo6y3nxyw5fg foreign key (event_id) references events;
alter table if exists event_venues add constraint FKevent_venues_event foreign key (event_id) references events;
alter table if exists event_tags add constraint FKiwoyitw224ykom58m5xnoa9y6 foreign key (event_id) references events;
alter table if exists event_links add constraint fk_event_links_event foreign key (event_id) references events;
create index events_geo_idx on events using gist (
    geography(st_setsrid(st_makepoint(longitude, latitude), 4326))
) where latitude is not null and longitude is not null and deleted = false;

create table smart_matches (
    deleted boolean not null default false,
    created_at timestamp(6),
    created_by uuid,
    id uuid not null,
    first_user_id uuid not null,
    second_user_id uuid not null,
    first_user_liked boolean,
    second_user_liked boolean,
    first_user_liked_at timestamp(6),
    second_user_liked_at timestamp(6),
    rank integer,
    score double precision,
    icebreaker_computed_at timestamp without time zone,
    primary key (id),
    constraint uk_smart_matches_first_second unique (first_user_id, second_user_id)
);
create index smart_matches_first_user_id_idx on smart_matches (first_user_id);
create index smart_matches_second_user_id_idx on smart_matches (second_user_id);
create index smart_matches_first_unseen_rank_idx
    on smart_matches (first_user_id, rank)
    where first_user_liked is null and deleted = false;

create table smart_match_icebreaker_events (
    id uuid not null,
    smart_match_id uuid not null,
    event_id uuid not null,
    score double precision not null default 0,
    primary key (id),
    constraint fk_smart_match_icebreaker_match
        foreign key (smart_match_id) references smart_matches (id) on delete cascade,
    constraint uk_smart_match_icebreaker_match_event unique (smart_match_id, event_id)
);
create index smart_match_icebreaker_events_match_id_idx
    on smart_match_icebreaker_events (smart_match_id);

create table smart_match_icebreaker_event_shared_tags (
    icebreaker_event_id uuid not null,
    tag_id uuid not null,
    primary key (icebreaker_event_id, tag_id),
    constraint fk_smart_match_icebreaker_shared_tag_event
        foreign key (icebreaker_event_id) references smart_match_icebreaker_events (id) on delete cascade
);

create table subscriptions (
    id uuid not null,
    created_by uuid,
    created_at timestamp(6) without time zone,
    deleted boolean not null default false,
    user_id uuid not null,
    provider varchar(16) not null,
    provider_subscription_id varchar(512) not null,
    product_id varchar(255),
    status varchar(32) not null,
    expires_at timestamp without time zone,
    environment varchar(32),
    raw_payload text,
    updated_at timestamp without time zone,
    constraint subscriptions_pkey primary key (id)
);
create unique index uk_subscriptions_provider_subscription_id
    on subscriptions (provider, provider_subscription_id)
    where deleted = false;
create index idx_subscriptions_user_id
    on subscriptions (user_id)
    where deleted = false;

create table webhook_events (
    id uuid not null,
    created_by uuid,
    created_at timestamp(6) without time zone,
    deleted boolean not null default false,
    provider varchar(16) not null,
    event_id varchar(512) not null,
    event_type varchar(128),
    processed_at timestamp without time zone,
    raw_payload text,
    constraint webhook_events_pkey primary key (id)
);
create unique index uk_webhook_events_provider_event_id
    on webhook_events (provider, event_id)
    where deleted = false;

create table payment_transactions (
    id uuid not null,
    created_by uuid,
    created_at timestamp(6) without time zone,
    deleted boolean not null default false,
    user_id uuid not null,
    provider varchar(16) not null,
    provider_transaction_id varchar(512) not null,
    provider_subscription_id varchar(512),
    amount_cents bigint not null,
    currency varchar(8) not null,
    status varchar(32) not null,
    description varchar(512),
    invoice_url varchar(1024),
    paid_at timestamp without time zone,
    raw_payload text,
    updated_at timestamp without time zone,
    constraint payment_transactions_pkey primary key (id)
);
create unique index uk_payment_transactions_provider_tx_id
    on payment_transactions (provider, provider_transaction_id)
    where deleted = false;
create index idx_payment_transactions_user_id
    on payment_transactions (user_id)
    where deleted = false;

create table friendships (
    deleted boolean not null default false,
    created_at timestamp(6),
    created_by uuid,
    id uuid not null,
    requester_id uuid not null,
    addressee_id uuid not null,
    status varchar(16) not null,
    responded_at timestamp(6),
    primary key (id),
    constraint friendships_status_check check (status in ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED')),
    constraint friendships_not_self_check check (requester_id <> addressee_id)
);
comment on column friendships.deleted is 'Soft-delete indicator';
create unique index uk_friendships_active_pair
    on friendships (
        least(requester_id, addressee_id),
        greatest(requester_id, addressee_id)
    )
    where status in ('PENDING', 'ACCEPTED') and deleted = false;
create index friendships_requester_id_idx on friendships (requester_id);
create index friendships_addressee_id_idx on friendships (addressee_id);
create index friendships_status_idx on friendships (status);

-- Unified DM + group-inquiry channel registry (no separate chat_group_inquiry_channels table).
create table chat_channels (
    deleted boolean not null default false,
    created_at timestamp(6),
    created_by uuid,
    id uuid not null,
    first_user_id uuid,
    second_user_id uuid,
    channel_id varchar(64) not null,
    kind varchar(32) not null default 'DM',
    interest_group_id uuid,
    initiator_user_id uuid,
    primary key (id),
    constraint uk_chat_channels_channel_id unique (channel_id),
    constraint chat_channels_kind_check check (kind in ('DM', 'GROUP_INQUIRY'))
);
create unique index uk_chat_channels_dm_pair
    on chat_channels (first_user_id, second_user_id)
    where kind = 'DM'
      and deleted = false
      and first_user_id is not null
      and second_user_id is not null;
create unique index uk_chat_channels_group_inquiry_pair
    on chat_channels (interest_group_id, initiator_user_id)
    where kind = 'GROUP_INQUIRY'
      and deleted = false
      and interest_group_id is not null
      and initiator_user_id is not null;
create index chat_channels_first_user_id_idx on chat_channels (first_user_id);
create index chat_channels_second_user_id_idx on chat_channels (second_user_id);
create index chat_channels_kind_idx on chat_channels (kind);
create index chat_channels_interest_group_id_idx on chat_channels (interest_group_id);
create index chat_channels_initiator_user_id_idx on chat_channels (initiator_user_id);

create table chat_channel_members (
    deleted boolean not null default false,
    created_at timestamp(6),
    created_by uuid,
    id uuid not null,
    chat_channel_id uuid not null,
    user_id uuid not null,
    joined_at timestamp(6) not null,
    primary key (id),
    constraint fk_chat_channel_members_channel
        foreign key (chat_channel_id) references chat_channels (id)
);
comment on column chat_channel_members.deleted is 'Soft-delete indicator';
create unique index uk_chat_channel_members_channel_user
    on chat_channel_members (chat_channel_id, user_id)
    where deleted = false;
create index chat_channel_members_user_id_idx on chat_channel_members (user_id);
create index chat_channel_members_chat_channel_id_idx on chat_channel_members (chat_channel_id);
