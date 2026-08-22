create table venue_slot_areas (
    id uuid not null,
    created_by uuid,
    created_at timestamp(6) without time zone,
    deleted boolean not null default false,
    slot_id uuid not null,
    name varchar(255) not null,
    price_minor bigint not null default 0,
    currency varchar(8) not null default 'gbp',
    capacity integer,
    sort_order integer not null default 0,
    constraint venue_slot_areas_pkey primary key (id),
    constraint uk_venue_slot_areas_slot_name unique (slot_id, name),
    constraint fk_venue_slot_areas_slot foreign key (slot_id) references venue_slots (id),
    constraint venue_slot_areas_price_check check (price_minor >= 0),
    constraint venue_slot_areas_capacity_check check (capacity is null or capacity > 0)
);

create index idx_venue_slot_areas_slot_id
    on venue_slot_areas (slot_id)
    where deleted = false;

alter table venue_bookings
    add column area_id uuid;

alter table venue_bookings
    add constraint fk_venue_bookings_area
        foreign key (area_id) references venue_slot_areas (id);

create index idx_venue_bookings_area_id
    on venue_bookings (area_id)
    where deleted = false and area_id is not null;
