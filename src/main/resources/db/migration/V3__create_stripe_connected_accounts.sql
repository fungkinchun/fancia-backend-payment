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
