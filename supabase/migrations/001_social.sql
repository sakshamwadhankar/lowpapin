-- Lowpapin social MVP
-- Apply this migration in the Supabase SQL editor before enabling the Social screen.

create extension if not exists "uuid-ossp";

create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    username text not null unique check (username = lower(username) and username ~ '^[a-z0-9_]{3,24}$'),
    display_name text not null default '',
    current_streak integer not null default 0 check (current_streak >= 0),
    best_streak integer not null default 0 check (best_streak >= 0),
    last_check_in_date date,
    last_check_in_under_limit boolean not null default false,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.friendships (
    id uuid primary key default uuid_generate_v4(),
    requester_id uuid not null references public.profiles(id) on delete cascade,
    addressee_id uuid not null references public.profiles(id) on delete cascade,
    status text not null default 'pending' check (status in ('pending', 'accepted', 'declined')),
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    check (requester_id <> addressee_id),
    unique (requester_id, addressee_id)
);

create table if not exists public.accountability_permissions (
    id uuid primary key default uuid_generate_v4(),
    owner_id uuid not null references public.profiles(id) on delete cascade,
    partner_id uuid not null references public.profiles(id) on delete cascade,
    requested_by uuid not null references public.profiles(id) on delete cascade,
    status text not null default 'pending' check (status in ('pending', 'approved', 'declined', 'revoked')),
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now()),
    check (owner_id <> partner_id),
    unique (owner_id, partner_id)
);

create table if not exists public.accountability_limits (
    id uuid primary key default uuid_generate_v4(),
    permission_id uuid not null unique references public.accountability_permissions(id) on delete cascade,
    owner_id uuid not null references public.profiles(id) on delete cascade,
    sponsor_id uuid not null references public.profiles(id) on delete cascade,
    app text not null default 'instagram_reels' check (app = 'instagram_reels'),
    daily_limit_seconds bigint not null check (daily_limit_seconds between 60 and 86400),
    active boolean not null default true,
    updated_at timestamptz not null default timezone('utc', now())
);

create table if not exists public.daily_checkins (
    user_id uuid not null references public.profiles(id) on delete cascade,
    check_date date not null,
    under_limit boolean not null,
    created_at timestamptz not null default timezone('utc', now()),
    primary key (user_id, check_date)
);

create index if not exists friendships_requester_idx on public.friendships(requester_id, status);
create index if not exists friendships_addressee_idx on public.friendships(addressee_id, status);
create index if not exists accountability_owner_idx on public.accountability_permissions(owner_id, status);
create index if not exists accountability_partner_idx on public.accountability_permissions(partner_id, status);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = timezone('utc', now());
    return new;
end;
$$;

drop trigger if exists profiles_set_updated_at on public.profiles;
create trigger profiles_set_updated_at before update on public.profiles
for each row execute function public.set_updated_at();

drop trigger if exists friendships_set_updated_at on public.friendships;
create trigger friendships_set_updated_at before update on public.friendships
for each row execute function public.set_updated_at();

drop trigger if exists accountability_set_updated_at on public.accountability_permissions;
create trigger accountability_set_updated_at before update on public.accountability_permissions
for each row execute function public.set_updated_at();

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer set search_path = public
as $$
declare
    requested_username text := lower(coalesce(new.raw_user_meta_data ->> 'username', 'user_' || left(new.id::text, 8)));
begin
    insert into public.profiles (id, username, display_name)
    values (
        new.id,
        case
            when requested_username ~ '^[a-z0-9_]{3,24}$' then requested_username
            else 'user_' || left(new.id::text, 8)
        end,
        coalesce(new.raw_user_meta_data ->> 'display_name', '')
    )
    on conflict (id) do nothing;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

alter table public.profiles enable row level security;
alter table public.friendships enable row level security;
alter table public.accountability_permissions enable row level security;
alter table public.accountability_limits enable row level security;
alter table public.daily_checkins enable row level security;

drop policy if exists profiles_public_read on public.profiles;
create policy profiles_public_read on public.profiles for select to authenticated using (true);

drop policy if exists friendships_member_read on public.friendships;
create policy friendships_member_read on public.friendships for select to authenticated
using (requester_id = auth.uid() or addressee_id = auth.uid());

drop policy if exists accountability_member_read on public.accountability_permissions;
create policy accountability_member_read on public.accountability_permissions for select to authenticated
using (owner_id = auth.uid() or partner_id = auth.uid());

drop policy if exists limits_member_read on public.accountability_limits;
create policy limits_member_read on public.accountability_limits for select to authenticated
using (owner_id = auth.uid() or sponsor_id = auth.uid());

drop policy if exists daily_checkins_owner_read on public.daily_checkins;
create policy daily_checkins_owner_read on public.daily_checkins for select to authenticated
using (user_id = auth.uid());

create or replace view public.my_friend_streaks with (security_invoker = true) as
select
    f.id as friendship_id,
    case when f.requester_id = auth.uid() then f.addressee_id else f.requester_id end as friend_id,
    p.username,
    p.display_name,
    p.current_streak,
    p.best_streak,
    p.last_check_in_date = current_date and p.last_check_in_under_limit as completed_today,
    ap.id as permission_id,
    ap.status as permission_status,
    (ap.partner_id = auth.uid() and ap.owner_id <> auth.uid()) as can_set_limit
from public.friendships f
join public.profiles p on p.id = case when f.requester_id = auth.uid() then f.addressee_id else f.requester_id end
left join public.accountability_permissions ap on
    ((ap.owner_id = p.id and ap.partner_id = auth.uid()) or (ap.partner_id = p.id and ap.owner_id = auth.uid()))
where f.status = 'accepted'
  and (f.requester_id = auth.uid() or f.addressee_id = auth.uid());

create or replace view public.my_friend_requests with (security_invoker = true) as
select
    f.id,
    f.requester_id,
    requester.username as requester_username,
    f.addressee_id,
    f.status
from public.friendships f
join public.profiles requester on requester.id = f.requester_id
where f.addressee_id = auth.uid() and f.status = 'pending';

create or replace view public.my_accountability_requests with (security_invoker = true) as
select
    ap.id,
    ap.owner_id,
    owner_profile.username as owner_username,
    ap.partner_id,
    partner_profile.username as partner_username,
    ap.requested_by,
    ap.status
from public.accountability_permissions ap
join public.profiles owner_profile on owner_profile.id = ap.owner_id
join public.profiles partner_profile on partner_profile.id = ap.partner_id
where (ap.owner_id = auth.uid() or ap.partner_id = auth.uid())
  and ap.status in ('pending', 'approved');

create or replace function public.send_friend_request(p_username text)
returns void
language plpgsql
security definer set search_path = public
as $$
declare
    target_id uuid;
begin
    select id into target_id from public.profiles where username = lower(trim(p_username));
    if target_id is null then raise exception 'Friend not found'; end if;
    if target_id = auth.uid() then raise exception 'You cannot add yourself'; end if;

    insert into public.friendships (requester_id, addressee_id, status)
    values (auth.uid(), target_id, 'pending')
    on conflict (requester_id, addressee_id) do update set status = 'pending', updated_at = timezone('utc', now());
end;
$$;

create or replace function public.respond_friend_request(p_friendship_id uuid, p_accept boolean)
returns void
language plpgsql
security definer set search_path = public
as $$
begin
    update public.friendships
    set status = case when p_accept then 'accepted' else 'declined' end
    where id = p_friendship_id and addressee_id = auth.uid() and status = 'pending';
    if not found then raise exception 'Friend request not found'; end if;
end;
$$;

create or replace function public.request_accountability_permission(p_friend_id uuid)
returns void
language plpgsql
security definer set search_path = public
as $$
begin
    if not exists (
        select 1 from public.friendships
        where status = 'accepted'
          and ((requester_id = auth.uid() and addressee_id = p_friend_id)
            or (requester_id = p_friend_id and addressee_id = auth.uid()))
    ) then raise exception 'You must be friends first'; end if;

    insert into public.accountability_permissions (owner_id, partner_id, requested_by, status)
    values (p_friend_id, auth.uid(), auth.uid(), 'pending')
    on conflict (owner_id, partner_id) do update
    set requested_by = auth.uid(), status = 'pending', updated_at = timezone('utc', now());
end;
$$;

create or replace function public.respond_accountability_permission(p_permission_id uuid, p_accept boolean)
returns void
language plpgsql
security definer set search_path = public
as $$
begin
    update public.accountability_permissions
    set status = case when p_accept then 'approved' else 'declined' end
    where id = p_permission_id and owner_id = auth.uid() and status = 'pending';
    if not found then raise exception 'Permission request not found'; end if;
end;
$$;

create or replace function public.set_accountability_limit(p_permission_id uuid, p_daily_limit_seconds bigint)
returns void
language plpgsql
security definer set search_path = public
as $$
declare
    permission_row public.accountability_permissions;
begin
    select * into permission_row from public.accountability_permissions
    where id = p_permission_id and partner_id = auth.uid() and status = 'approved';
    if permission_row.id is null then raise exception 'Permission is not approved'; end if;
    if p_daily_limit_seconds < 60 or p_daily_limit_seconds > 86400 then raise exception 'Limit must be between 1 minute and 24 hours'; end if;

    insert into public.accountability_limits (permission_id, owner_id, sponsor_id, daily_limit_seconds, active)
    values (permission_row.id, permission_row.owner_id, permission_row.partner_id, p_daily_limit_seconds, true)
    on conflict (permission_id) do update
    set daily_limit_seconds = excluded.daily_limit_seconds, active = true, updated_at = timezone('utc', now());
end;
$$;

create or replace function public.revoke_accountability_permission(p_permission_id uuid)
returns void
language plpgsql
security definer set search_path = public
as $$
begin
    update public.accountability_permissions
    set status = 'revoked'
    where id = p_permission_id and (owner_id = auth.uid() or partner_id = auth.uid());
    update public.accountability_limits set active = false where permission_id = p_permission_id;
end;
$$;

create or replace function public.get_my_active_accountability_limit()
returns table(permission_id uuid, daily_limit_seconds bigint, sponsor_id uuid, updated_at_epoch_millis bigint)
language sql
security definer set search_path = public
as $$
    select al.permission_id,
           al.daily_limit_seconds,
           al.sponsor_id,
           (extract(epoch from al.updated_at) * 1000)::bigint
    from public.accountability_limits al
    join public.accountability_permissions ap on ap.id = al.permission_id
    where al.owner_id = auth.uid() and al.active and ap.status = 'approved'
    order by al.updated_at desc
    limit 1;
$$;

create or replace function public.record_daily_check_in(p_under_limit boolean)
returns void
language plpgsql
security definer set search_path = public
as $$
declare
    profile_row public.profiles;
    next_streak integer;
begin
    select * into profile_row from public.profiles where id = auth.uid();
    if profile_row.id is null then raise exception 'Profile not found'; end if;
    if profile_row.last_check_in_date = current_date then return; end if;

    if p_under_limit and profile_row.last_check_in_under_limit and profile_row.last_check_in_date = current_date - 1 then
        next_streak := profile_row.current_streak + 1;
    elsif p_under_limit then
        next_streak := 1;
    else
        next_streak := 0;
    end if;

    insert into public.daily_checkins (user_id, check_date, under_limit)
    values (auth.uid(), current_date, p_under_limit)
    on conflict (user_id, check_date) do update set under_limit = excluded.under_limit;

    update public.profiles
    set current_streak = next_streak,
        best_streak = greatest(best_streak, next_streak),
        last_check_in_date = current_date,
        last_check_in_under_limit = p_under_limit
    where id = auth.uid();
end;
$$;

grant select on public.profiles, public.my_friend_streaks, public.my_friend_requests, public.my_accountability_requests to authenticated;
grant execute on function public.send_friend_request(text) to authenticated;
grant execute on function public.respond_friend_request(uuid, boolean) to authenticated;
grant execute on function public.request_accountability_permission(uuid) to authenticated;
grant execute on function public.respond_accountability_permission(uuid, boolean) to authenticated;
grant execute on function public.set_accountability_limit(uuid, bigint) to authenticated;
grant execute on function public.revoke_accountability_permission(uuid) to authenticated;
grant execute on function public.get_my_active_accountability_limit() to authenticated;
grant execute on function public.record_daily_check_in(boolean) to authenticated;
