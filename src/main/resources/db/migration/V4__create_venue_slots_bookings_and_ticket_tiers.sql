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
    constraint venue_bookings_pkey primary key (id),
    constraint fk_venue_bookings_venue foreign key (venue_id) references venues (id),
    constraint fk_venue_bookings_slot foreign key (slot_id) references venue_slots (id),
    constraint venue_bookings_status_check check (
        status in (
            'REQUESTED',
            'APPROVED',
            'PAID',
            'COMPLETED',
            'DENIED',
            'WITHDRAWN',
            'CANCELLED',
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
    constraint event_ticket_tiers_pkey primary key (id),
    constraint fk_event_ticket_tiers_event foreign key (event_id) references events (id),
    constraint event_ticket_tiers_price_check check (price_minor >= 0),
    constraint event_ticket_tiers_capacity_check
        check (capacity_per_occurrence is null or capacity_per_occurrence > 0)
);
create index idx_event_ticket_tiers_event_id
    on event_ticket_tiers (event_id)
    where deleted = false;

do $$
declare
    cname text;
begin
    for cname in
        select con.conname
        from pg_constraint con
        join pg_class rel on rel.oid = con.conrelid
        where rel.relname = 'reservations'
          and con.contype = 'c'
          and pg_get_constraintdef(con.oid) ilike '%status%'
          and pg_get_constraintdef(con.oid) ilike '%PENDING%'
    loop
        execute format('alter table reservations drop constraint %I', cname);
    end loop;
end $$;
alter table reservations
    add constraint reservations_status_check
    check (status in ('PENDING', 'PAID', 'ACCEPTED', 'WHITELIST', 'DENIED', 'WITHDREW'));

alter table reservations add column tier_id uuid;
alter table reservations add column price_minor bigint;
alter table reservations add column currency varchar(8);
alter table reservations add column stripe_checkout_session_id varchar(255);
alter table reservations add column paid_at timestamp(6) without time zone;
alter table reservations
    add constraint fk_reservations_tier
    foreign key (tier_id) references event_ticket_tiers (id);
create index idx_reservations_tier_occurrence
    on reservations (tier_id, occurrence_id)
    where tier_id is not null;
