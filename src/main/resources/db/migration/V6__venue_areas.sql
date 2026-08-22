-- Move bookable areas from per-slot (venue_slot_areas) to per-venue (venue_areas).
-- Bookings keep area_id; capacity checks use overlapping slot time windows in app code.

alter table venue_bookings
    drop constraint if exists fk_venue_bookings_area;

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
    constraint uk_venue_areas_venue_name unique (venue_id, name),
    constraint fk_venue_areas_venue foreign key (venue_id) references venues (id),
    constraint venue_areas_price_check check (price_minor >= 0),
    constraint venue_areas_capacity_check check (capacity is null or capacity > 0)
);

create index idx_venue_areas_venue_id
    on venue_areas (venue_id)
    where deleted = false;

-- One venue area per (venue, name); keep the earliest slot-area row as the canonical id.
insert into venue_areas (
    id,
    created_by,
    created_at,
    deleted,
    venue_id,
    name,
    price_minor,
    currency,
    capacity,
    sort_order
)
select distinct on (vs.venue_id, vsa.name)
    vsa.id,
    vsa.created_by,
    vsa.created_at,
    vsa.deleted,
    vs.venue_id,
    vsa.name,
    vsa.price_minor,
    vsa.currency,
    vsa.capacity,
    vsa.sort_order
from venue_slot_areas vsa
join venue_slots vs on vs.id = vsa.slot_id
order by vs.venue_id, vsa.name, vsa.sort_order asc, vsa.created_at asc nulls last, vsa.id asc;

-- Point any booking still on a non-canonical slot-area id at the venue area with the same name.
update venue_bookings b
set area_id = mapped.venue_area_id
from (
    select
        vsa.id as slot_area_id,
        va.id as venue_area_id
    from venue_slot_areas vsa
    join venue_slots vs on vs.id = vsa.slot_id
    join venue_areas va
        on va.venue_id = vs.venue_id
        and va.name = vsa.name
) mapped
where b.area_id = mapped.slot_area_id
  and b.area_id is distinct from mapped.venue_area_id;

drop table if exists venue_slot_areas;

alter table venue_bookings
    add constraint fk_venue_bookings_area
        foreign key (area_id) references venue_areas (id);
