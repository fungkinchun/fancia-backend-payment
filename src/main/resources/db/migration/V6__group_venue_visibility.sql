alter table interest_groups
    add column if not exists visibility varchar(16) not null default 'PUBLIC';

alter table interest_groups
    drop constraint if exists interest_groups_visibility_check;

alter table interest_groups
    add constraint interest_groups_visibility_check
        check (visibility in ('PUBLIC', 'PRIVATE'));

alter table venues
    add column if not exists visibility varchar(16) not null default 'PUBLIC';

alter table venues
    drop constraint if exists venues_visibility_check;

alter table venues
    add constraint venues_visibility_check
        check (visibility in ('PUBLIC', 'PRIVATE'));

