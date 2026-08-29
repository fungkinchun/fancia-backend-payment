alter table chat_channels drop constraint chat_channels_kind_check;
alter table chat_channels
    add constraint chat_channels_kind_check check (kind in ('DM', 'GROUP_INQUIRY', 'SUPPORT'));

create unique index uk_chat_channels_support_initiator
    on chat_channels (initiator_user_id)
    where kind = 'SUPPORT'
      and deleted = false
      and initiator_user_id is not null;
