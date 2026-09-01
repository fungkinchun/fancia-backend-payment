create table if not exists referrals (
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

create unique index if not exists uk_referrals_referee on referrals (referee_user_id) where deleted = false;
create index if not exists idx_referrals_referrer on referrals (referrer_user_id) where deleted = false;
