alter table event_ticket_tiers
    add column check_in_before_minutes integer not null default 120,
    add column check_in_after_minutes integer not null default 60;

alter table event_ticket_tiers
    add constraint event_ticket_tiers_check_in_before_check
        check (check_in_before_minutes >= 0),
    add constraint event_ticket_tiers_check_in_after_check
        check (check_in_after_minutes >= 0);

alter table reservations
    add column check_in_token varchar(64),
    add column checked_in_at timestamp(6) without time zone,
    add column checked_in_by uuid;

create unique index uk_reservations_check_in_token
    on reservations (check_in_token)
    where check_in_token is not null;

create index idx_reservations_occurrence_accepted_token
    on reservations (occurrence_id)
    where status = 'ACCEPTED' and check_in_token is not null;
