CREATE EXTENSION IF NOT EXISTS pg_trgm;

create table "authorization_consents" (authorities varchar(1000), "client_id" varchar(255), principal_name varchar(255) not null, registered_client_id varchar(255) not null, primary key (principal_name, registered_client_id));
create table "authorizations" (access_token_expires_at timestamp(6) with time zone, access_token_issued_at timestamp(6) with time zone, authorization_code_expires_at timestamp(6) with time zone, authorization_code_issued_at timestamp(6) with time zone, device_code_expires_at timestamp(6) with time zone, device_code_issued_at timestamp(6) with time zone, oidc_id_token_expires_at timestamp(6) with time zone, oidc_id_token_issued_at timestamp(6) with time zone, refresh_token_expires_at timestamp(6) with time zone, refresh_token_issued_at timestamp(6) with time zone, user_code_expires_at timestamp(6) with time zone, user_code_issued_at timestamp(6) with time zone, state varchar(500), access_token_scopes varchar(1000), authorized_scopes varchar(1000), access_token_metadata varchar(2000), device_code_metadata varchar(2000), oidc_id_token_claims varchar(2000), oidc_id_token_metadata varchar(2000), refresh_token_metadata varchar(2000), user_code_metadata varchar(2000), access_token_value varchar(32600), attributes varchar(32600), authorization_code_value varchar(32600), device_code_value varchar(32600), oidc_id_token_value varchar(32600), refresh_token_value varchar(32600), user_code_value varchar(32600), access_token_type varchar(255), authorization_code_metadata varchar(255), authorization_grant_type varchar(255), "client_id" varchar(255), id varchar(255) not null, principal_name varchar(255), primary key (id));
create table "clients" (client_id_issued_at timestamp(6) with time zone, client_secret_expires_at timestamp(6) with time zone, authorization_grant_types varchar(1000), client_authentication_methods varchar(1000), post_logout_redirect_uris varchar(1000), redirect_uris varchar(1000), scopes varchar(1000), client_settings varchar(2000), token_settings varchar(2000), client_id varchar(255) unique, client_name varchar(255), client_secret varchar(255), id varchar(255) not null, primary key (id));
create table password_reset_tokens (deleted boolean not null, email_sent boolean not null, created_at timestamp(6), expires_at timestamp(6), created_by uuid, id uuid not null, "user_id" uuid, token varchar(255), primary key (id));
create table user_connected_accounts (deleted boolean not null, connected_at timestamp(6), created_at timestamp(6), created_by uuid, id uuid not null, user_id uuid, provider varchar(255), provider_id varchar(255), primary key (id));
create table user_links (user_id uuid not null, type varchar(50) not null check ((type in ('WEBSITE','INSTAGRAM','FACEBOOK','TWITTER','LINKEDIN','YOUTUBE','TIKTOK'))), url varchar(255) not null, primary key (user_id, type, url));
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
    slug varchar(255),
    slug_changed_at timestamp(6),
    primary key (id)
);
create table tags (deleted boolean not null, created_at timestamp(6), created_by uuid, id uuid not null, name varchar(50) not null, type varchar(32) not null check (type in ('INTEREST', 'SKILL', 'TOPIC', 'SYSTEM')), primary key (id), unique (name, type));
create table user_tags (user_id uuid not null, tag_id uuid not null, primary key (user_id, tag_id));
create table user_settings (user_id uuid primary key, privacy jsonb not null default '{}', notifications jsonb not null default '{}');
create table verification_codes (deleted boolean not null, email_sent boolean not null, created_at timestamp(6), created_by uuid, id uuid not null, "user_id" uuid unique, code varchar(255), primary key (id));
alter table if exists "authorization_consents" add constraint FK7u3rrcx79xyss37m2551mpx2p foreign key ("client_id") references "clients";
alter table if exists "authorizations" add constraint FK4ehcr3h1eun20h36is62nal65 foreign key ("client_id") references "clients";
alter table if exists password_reset_tokens add constraint FKrjxrqd0dudi212f0469dojcod foreign key ("user_id") references "users";
alter table if exists user_connected_accounts add constraint FKnnce63ye8wbmdskoeco5ku43d foreign key (user_id) references "users";
create table stripe_connected_accounts (
    id uuid not null,
    country varchar(2),
    default_currency varchar(8),
    charges_enabled boolean not null default false,
    payouts_enabled boolean not null default false,
    details_submitted boolean not null default false,
    disabled_reason varchar(255),
    onboarded_at timestamp without time zone,
    raw_payload text,
    updated_at timestamp without time zone,
    constraint stripe_connected_accounts_pkey primary key (id),
    constraint fk_stripe_connected_accounts_user_connected_account
        foreign key (id) references user_connected_accounts (id) on delete cascade
);

create unique index uk_user_connected_accounts_stripe_user
    on user_connected_accounts (user_id)
    where deleted = false and provider = 'stripe';

create unique index uk_user_connected_accounts_stripe_provider_id
    on user_connected_accounts (provider_id)
    where deleted = false and provider = 'stripe';
alter table if exists user_links add constraint FK4wc3hhebo87m149hnxkxxmfvm foreign key (user_id) references "users";
alter table if exists user_tags add constraint FK_user_tags_user foreign key (user_id) references "users";
alter table if exists user_settings add constraint FK_user_settings_user foreign key (user_id) references "users";
alter table if exists verification_codes add constraint FK2c664upaiv1f6h7e5ueyy1ae3 foreign key ("user_id") references "users";

create table comment_likes (liked_at timestamp(6), comment_id uuid not null, user_id uuid not null, primary key (comment_id, user_id));
create table comments (deleted boolean not null, created_at timestamp(6), author_user_id uuid not null, created_by uuid, id uuid not null, resource_id uuid, target_id uuid not null, body varchar(4000) not null, primary key (id));
create table post_likes (liked_at timestamp(6), post_id uuid not null, user_id uuid not null, primary key (post_id, user_id));
create table post_media (deleted boolean not null, sort_order integer not null, created_at timestamp(6), created_by uuid, id uuid not null, media_type varchar(16) not null check ((media_type in ('IMAGE','VIDEO'))), post_id uuid not null, object_key varchar(1024) not null, primary key (id));
create table posts (
    deleted boolean not null,
    created_at timestamp(6),
    author_user_id uuid not null,
    created_by uuid,
    id uuid not null,
    target_id uuid not null,
    body varchar(4000),
    kind varchar(16) not null default 'TEXT',
    status varchar(16) not null default 'VISIBLE',
    expired_at timestamp(6),
    primary key (id),
    constraint posts_kind_check check (kind in ('TEXT', 'POLL')),
    constraint posts_status_check check (status in ('VISIBLE', 'FEATURED', 'PINNED', 'READ_ONLY', 'HIDDEN'))
);
create index posts_expired_at_status_idx on posts (expired_at, status)
    where deleted = false and expired_at is not null;
alter table if exists comment_likes add constraint FK3wa5u7bs1p1o9hmavtgdgk1go foreign key (comment_id) references comments;
alter table if exists post_likes add constraint FKa5wxsgl4doibhbed9gm7ikie2 foreign key (post_id) references posts;
alter table if exists post_media add constraint FK1urcum9dtf0vgul7k405f4r2d foreign key (post_id) references posts;

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

create table interest_groups (
    deleted boolean not null,
    created_at timestamp(6),
    created_by uuid,
    id uuid not null,
    description varchar(4000) not null,
    name varchar(255) not null,
    slug varchar(255) not null,
    visibility varchar(16) not null default 'PUBLIC'
        check (visibility in ('PUBLIC', 'PRIVATE')),
    invite_token varchar(64),
    primary key (id)
);
create table interest_group_links (interest_group_id uuid not null, type varchar(50) not null check ((type in ('WEBSITE','INSTAGRAM','FACEBOOK','TWITTER','LINKEDIN','YOUTUBE','TIKTOK'))), url varchar(255) not null, primary key (interest_group_id, type, url));
create table interest_group_memberships (joined_at timestamp(6), interest_group_id uuid not null, user_id uuid not null, role varchar(255) check ((role in ('ADMIN','MEMBER'))), status varchar(255) check ((status in ('ACCEPTED','PENDING','DENIED','WITHDREW','BANNED'))), primary key (interest_group_id, user_id));
create table interest_group_tags (interest_group_id uuid not null, tag_id uuid not null, primary key (interest_group_id, tag_id));
alter table if exists interest_group_links add constraint FK3519psi1d5n0prhs7ectxqyfy foreign key (interest_group_id) references interest_groups;
alter table if exists interest_group_memberships add constraint FK969x3gmh9kq16vevdr74h0t3g foreign key (interest_group_id) references interest_groups;
alter table if exists interest_group_tags add constraint FKcbscsmlvmrmdqc0ih8c6dlgkk foreign key (interest_group_id) references interest_groups;

create table venues (
    deleted boolean not null,
    latitude double precision,
    longitude double precision,
    created_at timestamp(6),
    created_by uuid,
    id uuid not null,
    postcode varchar(50),
    country varchar(100),
    address_line varchar(500),
    location_label varchar(500),
    description varchar(4000) not null,
    city varchar(255),
    name varchar(255) not null,
    slug varchar(255) not null,
    place_id varchar(255),
    visibility varchar(16) not null default 'PUBLIC'
        check (visibility in ('PUBLIC', 'PRIVATE')),
    primary key (id)
);
create table venue_links (venue_id uuid not null, type varchar(50) not null check ((type in ('WEBSITE','INSTAGRAM','FACEBOOK','TWITTER','LINKEDIN','YOUTUBE','TIKTOK','ZOOM','TEAMS','GOOGLE_MEET'))), url varchar(255) not null, primary key (venue_id, type, url));
create table venue_staff (joined_at timestamp(6), venue_id uuid not null, user_id uuid not null, role varchar(255) check ((role in ('ADMIN','MEMBER'))), status varchar(255) check ((status in ('ACCEPTED','PENDING','DENIED','WITHDREW','BANNED'))), primary key (venue_id, user_id));
create table venue_tags (venue_id uuid not null, tag_id uuid not null, primary key (venue_id, tag_id));
alter table if exists venue_links add constraint FKvenue_links_venue foreign key (venue_id) references venues;
alter table if exists venue_staff add constraint FKvenue_staff_venue foreign key (venue_id) references venues;
alter table if exists venue_tags add constraint FKvenue_tags_venue foreign key (venue_id) references venues;
create index venues_geo_idx on venues using gist (
    geography(st_setsrid(st_makepoint(longitude, latitude), 4326))
) where latitude is not null and longitude is not null and deleted = false;

create table venue_slots (
    id uuid not null,
    created_by uuid,
    created_at timestamp(6) without time zone,
    deleted boolean not null default false,
    venue_id uuid not null,
    start_time timestamp(6) without time zone not null,
    end_time timestamp(6) without time zone not null,
    price_minor bigint not null default 0,
    currency varchar(8) not null default 'gbp',
    status varchar(32) not null,
    constraint venue_slots_pkey primary key (id),
    constraint fk_venue_slots_venue foreign key (venue_id) references venues (id),
    constraint venue_slots_status_check check (status in ('DRAFT', 'PUBLISHED', 'BOOKED', 'CANCELLED')),
    constraint venue_slots_time_check check (end_time > start_time),
    constraint venue_slots_price_check check (price_minor >= 0)
);
create index idx_venue_slots_venue_id
    on venue_slots (venue_id)
    where deleted = false;
create index idx_venue_slots_venue_status_start
    on venue_slots (venue_id, status, start_time)
    where deleted = false;

create table venue_areas (
    id uuid not null,
    created_by uuid,
    created_at timestamp(6) without time zone,
    deleted boolean not null default false,
    venue_id uuid not null,
    name varchar(255) not null,
    price_minor bigint not null default 0,
    currency varchar(8) not null default 'gbp',
    capacity integer,
    sort_order integer not null default 0,
    constraint venue_areas_pkey primary key (id),
    constraint fk_venue_areas_venue foreign key (venue_id) references venues (id),
    constraint venue_areas_price_check check (price_minor >= 0),
    constraint venue_areas_capacity_check check (capacity is null or capacity > 0)
);
create index idx_venue_areas_venue_id
    on venue_areas (venue_id)
    where deleted = false;
create unique index uk_venue_areas_venue_name_active
    on venue_areas (venue_id, name)
    where deleted = false;

create table venue_bookings (
    id uuid not null,
    created_by uuid,
    created_at timestamp(6) without time zone,
    deleted boolean not null default false,
    venue_id uuid not null,
    slot_id uuid not null,
    requester_user_id uuid not null,
    status varchar(32) not null,
    price_minor bigint not null,
    currency varchar(8) not null default 'gbp',
    stripe_checkout_session_id varchar(255),
    paid_at timestamp(6) without time zone,
    area_id uuid,
    constraint venue_bookings_pkey primary key (id),
    constraint fk_venue_bookings_venue foreign key (venue_id) references venues (id),
    constraint fk_venue_bookings_slot foreign key (slot_id) references venue_slots (id),
    constraint fk_venue_bookings_area foreign key (area_id) references venue_areas (id),
    constraint venue_bookings_status_check check (
        status in (
            'REQUESTED',
            'PAID',
            'ACCEPTED',
            'DENIED',
            'WITHDRAWN',
            'EXPIRED'
        )
    ),
    constraint venue_bookings_price_check check (price_minor >= 0)
);
create index idx_venue_bookings_venue_id
    on venue_bookings (venue_id)
    where deleted = false;
create index idx_venue_bookings_slot_id
    on venue_bookings (slot_id)
    where deleted = false;
create index idx_venue_bookings_requester
    on venue_bookings (requester_user_id)
    where deleted = false;
create index idx_venue_bookings_venue_status
    on venue_bookings (venue_id, status)
    where deleted = false;
create index idx_venue_bookings_area_id
    on venue_bookings (area_id)
    where deleted = false and area_id is not null;

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
    event_type varchar(32) not null default 'REGULAR'
        check (event_type in ('REGULAR', 'SPONTANEOUS')),
    visibility varchar(255) not null check ((visibility in ('PUBLIC','GROUP','PRIVATE'))),
    invite_token varchar(64),
    recurrence_frequency varchar(32) not null default 'NONE',
    recurrence_days_mask smallint not null default 0,
    recurrence_paused_until timestamp(6) with time zone,
    approval_required boolean not null default true,
    slug varchar(255) not null,
    primary key (id)
);
create index events_event_type_idx on events (event_type) where deleted = false;
create table event_interest_groups (event_id uuid not null, event_interest_groups uuid);
create table event_tags (event_id uuid not null, tag_id uuid not null, primary key (event_id, tag_id));
create table event_links (event_id uuid not null, type varchar(50) not null check ((type in ('WEBSITE','INSTAGRAM','FACEBOOK','TWITTER','LINKEDIN','YOUTUBE','TIKTOK','ZOOM','TEAMS','GOOGLE_MEET'))), url varchar(255) not null, primary key (event_id, type, url));
create table event_time_slots (
    id uuid not null,
    deleted boolean not null default false,
    created_at timestamp(6),
    created_by uuid,
    event_id uuid not null,
    start_time timestamp(6) not null,
    end_time timestamp(6) not null,
    sort_order integer not null default 0,
    primary key (id),
    constraint fk_event_time_slots_event foreign key (event_id) references events (id),
    constraint event_time_slots_time_check check (end_time > start_time),
    constraint uk_event_time_slots_event_start unique (event_id, start_time)
);
create index event_time_slots_event_id_idx on event_time_slots (event_id);
create table event_occurrences (
    deleted boolean not null default false,
    created_at timestamp(6),
    created_by uuid,
    id uuid not null,
    event_id uuid not null,
    time_slot_id uuid,
    start_time timestamp(6) not null,
    end_time timestamp(6) not null,
    status varchar(32) not null default 'SCHEDULED'
        check (status in ('SCHEDULED', 'CANCELLED')),
    primary key (id),
    constraint uk_event_occurrences_event_start unique (event_id, start_time),
    constraint fk_event_occurrences_event foreign key (event_id) references events (id),
    constraint fk_event_occurrences_time_slot
        foreign key (time_slot_id) references event_time_slots (id)
        on delete set null
);
create index event_occurrences_event_id_idx on event_occurrences (event_id);
create index event_occurrences_start_time_idx on event_occurrences (start_time);
create index event_occurrences_time_slot_id_idx on event_occurrences (time_slot_id);
create table event_participants (
    occurrence_id uuid not null,
    user_id uuid not null,
    role varchar(255) check ((role in ('HOST','COHOST','GUEST'))),
    primary key (occurrence_id, user_id),
    constraint fk_event_participants_occurrence foreign key (occurrence_id) references event_occurrences (id)
);
create table event_ticket_tiers (
    id uuid not null,
    created_by uuid,
    created_at timestamp(6) without time zone,
    deleted boolean not null default false,
    event_id uuid not null,
    name varchar(255) not null,
    price_minor bigint not null default 0,
    currency varchar(8) not null default 'gbp',
    capacity_per_occurrence integer,
    sort_order integer not null default 0,
    check_in_before_minutes integer not null default 120,
    check_in_after_minutes integer not null default 60,
    constraint event_ticket_tiers_pkey primary key (id),
    constraint fk_event_ticket_tiers_event foreign key (event_id) references events (id),
    constraint event_ticket_tiers_price_check check (price_minor >= 0),
    constraint event_ticket_tiers_capacity_check
        check (capacity_per_occurrence is null or capacity_per_occurrence > 0),
    constraint event_ticket_tiers_check_in_before_check
        check (check_in_before_minutes >= 0),
    constraint event_ticket_tiers_check_in_after_check
        check (check_in_after_minutes >= 0)
);
create index idx_event_ticket_tiers_event_id
    on event_ticket_tiers (event_id)
    where deleted = false;

create table reservations (
    guests integer not null,
    occurrence_id uuid not null,
    user_id uuid not null,
    payload varchar(4000),
    status varchar(255) check ((status in ('PENDING','PAID','ACCEPTED','WHITELIST','DENIED','WITHDREW'))),
    tier_id uuid,
    price_minor bigint,
    currency varchar(8),
    stripe_checkout_session_id varchar(255),
    paid_at timestamp(6) without time zone,
    check_in_token varchar(64),
    checked_in_at timestamp(6) without time zone,
    checked_in_by uuid,
    primary key (occurrence_id, user_id),
    constraint fk_reservations_occurrence foreign key (occurrence_id) references event_occurrences (id),
    constraint fk_reservations_tier foreign key (tier_id) references event_ticket_tiers (id)
);
create index idx_reservations_tier_occurrence
    on reservations (tier_id, occurrence_id)
    where tier_id is not null;
create unique index uk_reservations_check_in_token
    on reservations (check_in_token)
    where check_in_token is not null;
create index idx_reservations_occurrence_accepted_token
    on reservations (occurrence_id)
    where status = 'ACCEPTED' and check_in_token is not null;
alter table if exists event_interest_groups add constraint FK9pyxt3n5c0gtivo6y3nxyw5fg foreign key (event_id) references events;
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
create unique index uk_friendships_active_pair
    on friendships (
        least(requester_id, addressee_id),
        greatest(requester_id, addressee_id)
    )
    where status in ('PENDING', 'ACCEPTED') and deleted = false;
create index friendships_requester_id_idx on friendships (requester_id);
create index friendships_addressee_id_idx on friendships (addressee_id);
create index friendships_status_idx on friendships (status);

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
    event_id uuid,
    primary key (id),
    constraint uk_chat_channels_channel_id unique (channel_id),
    constraint chat_channels_kind_check check (kind in ('DM', 'GROUP_INQUIRY', 'SUPPORT', 'EVENT'))
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
create unique index uk_chat_channels_support_initiator
    on chat_channels (initiator_user_id)
    where kind = 'SUPPORT'
      and deleted = false
      and initiator_user_id is not null;
create unique index uk_chat_channels_event
    on chat_channels (event_id)
    where kind = 'EVENT'
      and deleted = false
      and event_id is not null;
create index chat_channels_first_user_id_idx on chat_channels (first_user_id);
create index chat_channels_second_user_id_idx on chat_channels (second_user_id);
create index chat_channels_kind_idx on chat_channels (kind);
create index chat_channels_interest_group_id_idx on chat_channels (interest_group_id);
create index chat_channels_initiator_user_id_idx on chat_channels (initiator_user_id);
create index chat_channels_event_id_idx on chat_channels (event_id);

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
create unique index uk_chat_channel_members_channel_user
    on chat_channel_members (chat_channel_id, user_id)
    where deleted = false;
create index chat_channel_members_user_id_idx on chat_channel_members (user_id);
create index chat_channel_members_chat_channel_id_idx on chat_channel_members (chat_channel_id);

create unique index uk_events_slug on events (slug) where deleted = false;
create unique index uk_events_invite_token on events (invite_token) where invite_token is not null and deleted = false;
create unique index uk_venues_slug on venues (slug) where deleted = false;
create unique index uk_interest_groups_slug on interest_groups (slug) where deleted = false;
create unique index uk_interest_groups_invite_token on interest_groups (invite_token) where invite_token is not null and deleted = false;
create unique index uk_users_slug on users (slug) where deleted = false and slug is not null;

create table user_slug_history (
    slug varchar(255) not null,
    user_id uuid not null,
    created_at timestamp(6) not null default now(),
    constraint user_slug_history_pkey primary key (slug),
    constraint fk_user_slug_history_user foreign key (user_id) references users (id)
);
create index idx_user_slug_history_user_id on user_slug_history (user_id);

create table referrals (
    id uuid not null,
    deleted boolean not null default false,
    created_at timestamp(6),
    created_by uuid,
    referrer_user_id uuid not null,
    referee_user_id uuid not null,
    referrer_slug varchar(255) not null,
    rewarded_at timestamp(6) not null,
    primary key (id),
    constraint fk_referrals_referrer foreign key (referrer_user_id) references users (id),
    constraint fk_referrals_referee foreign key (referee_user_id) references users (id)
);

create unique index uk_referrals_referee on referrals (referee_user_id) where deleted = false;
create index idx_referrals_referrer on referrals (referrer_user_id) where deleted = false;

create table blocked_resources (
    user_id uuid not null,
    resource_type varchar(32) not null
        check (resource_type in ('USER', 'POST', 'COMMENT', 'EVENT', 'INTEREST_GROUP', 'VENUE', 'TAG')),
    resource_id uuid not null,
    created_at timestamp(6),
    primary key (user_id, resource_type, resource_id)
);

create index idx_blocked_resources_user_type
    on blocked_resources (user_id, resource_type);

create table reports (
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
    primary key (id)
);

create index idx_reports_reporter on reports (reporter_user_id) where deleted = false;
create index idx_reports_target on reports (target_type, target_id) where deleted = false;

create table saved_resources (
    user_id uuid not null,
    resource_id uuid not null,
    created_at timestamp(6),
    primary key (user_id, resource_id)
);

create index idx_saved_resources_user_created
    on saved_resources (user_id, created_at desc);
