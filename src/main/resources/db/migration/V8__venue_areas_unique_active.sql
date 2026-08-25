-- Soft-deleted areas still occupied uk_venue_areas_venue_name, so recreating
-- "VIP" after delete (or save that deletes+recreates) failed with 23505.
-- Unique names only among active (deleted = false) rows.

alter table venue_areas
    drop constraint if exists uk_venue_areas_venue_name;

create unique index uk_venue_areas_venue_name_active
    on venue_areas (venue_id, name)
    where deleted = false;
