-- LockedIn backend foundation (Supabase Postgres).
-- Covers: server-side Plus entitlements (admin-granted only) and Gemini request quotas
-- with configurable limits. Requires Supabase's auth schema (auth.users, auth.uid()).

-- ---------------------------------------------------------------------------
-- Entitlements
-- ---------------------------------------------------------------------------

create table if not exists public.entitlements (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users (id) on delete cascade,
  product     text not null default 'plus' check (product = 'plus'),
  starts_at   timestamptz not null default now(),
  ends_at     timestamptz not null,
  -- Where the entitlement came from. Only 'admin' is written until payment is wired up.
  source      text not null check (source in ('admin', 'app_store', 'play', 'web')),
  granted_by  text not null,
  note        text,
  revoked_at  timestamptz,
  created_at  timestamptz not null default now(),
  check (ends_at > starts_at)
);

create index if not exists entitlements_user_ends_idx
  on public.entitlements (user_id, ends_at desc);

-- True when the user has an unrevoked Plus entitlement covering now().
create or replace function public.plus_active(p_user uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.entitlements e
    where e.user_id = p_user
      and e.revoked_at is null
      and e.starts_at <= now()
      and e.ends_at > now()
  );
$$;

-- The caller's own Plus expiry (RLS limits this to their rows).
create or replace function public.my_plus_until()
returns timestamptz
language sql
stable
security invoker
set search_path = public
as $$
  select max(e.ends_at)
  from public.entitlements e
  where e.user_id = auth.uid()
    and e.revoked_at is null
    and e.ends_at > now();
$$;

-- Admin grant. Only the Supabase service role or the database owner may call it.
-- Stacks onto any existing Plus: the new period starts when the current one ends.
create or replace function public.admin_grant_plus(
  p_user  uuid,
  p_days  integer,
  p_note  text default null
)
returns public.entitlements
language plpgsql
security definer
set search_path = public
as $$
declare
  caller  text := coalesce(nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'role', '');
  start_at timestamptz;
  granted public.entitlements;
begin
  -- session_user is the connecting role. It is not changed by SECURITY DEFINER, unlike current_user.
  if not (session_user in ('postgres', 'service_role') or caller = 'service_role') then
    raise exception 'not authorised' using errcode = '42501';
  end if;
  if p_days is null or p_days < 1 or p_days > 366 then
    raise exception 'p_days must be between 1 and 366';
  end if;
  if not exists (select 1 from auth.users u where u.id = p_user) then
    raise exception 'unknown user';
  end if;

  select greatest(now(), coalesce(max(e.ends_at), now()))
    into start_at
  from public.entitlements e
  where e.user_id = p_user and e.revoked_at is null;

  insert into public.entitlements (user_id, starts_at, ends_at, source, granted_by, note)
  values (p_user, start_at, start_at + make_interval(days => p_days), 'admin', session_user, p_note)
  returning * into granted;

  return granted;
end;
$$;

-- Admin revocation (for example a refund). Sets revoked_at; the row is kept for audit.
create or replace function public.admin_revoke_plus(p_entitlement uuid, p_reason text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  caller text := coalesce(nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'role', '');
begin
  if not (session_user in ('postgres', 'service_role') or caller = 'service_role') then
    raise exception 'not authorised' using errcode = '42501';
  end if;
  if p_reason is null or length(trim(p_reason)) = 0 then
    raise exception 'a revocation reason is required';
  end if;
  update public.entitlements
     set revoked_at = now(),
         note = coalesce(note, '') || ' | revoked: ' || p_reason
   where id = p_entitlement and revoked_at is null;
  if not found then
    raise exception 'no active entitlement with that id';
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- AI quota and configurable limits
-- ---------------------------------------------------------------------------

-- Limits the founder can change without redeploying. Missing or zero values fail closed.
create table if not exists public.ai_settings (
  key    text primary key
         check (key in ('ai_enabled', 'free_daily_requests', 'plus_daily_requests',
                        'global_daily_requests', 'max_output_tokens')),
  value  integer not null check (value >= 0)
);

insert into public.ai_settings (key, value) values
  ('ai_enabled', 1),              -- 1 = on, 0 = off (kill switch)
  ('free_daily_requests', 5),     -- AI requests per user per day, free users
  ('plus_daily_requests', 100),   -- AI requests per user per day, Plus users
  ('global_daily_requests', 2000),-- AI requests per day across all users (spend ceiling)
  ('max_output_tokens', 1024)     -- passed to Gemini as the output cap per request
on conflict (key) do nothing;

create table if not exists public.ai_user_usage (
  user_id    uuid not null references auth.users (id) on delete cascade,
  usage_day  date not null,
  requests   integer not null default 0 check (requests >= 0),
  primary key (user_id, usage_day)
);

create table if not exists public.ai_global_usage (
  usage_day  date primary key,
  requests   integer not null default 0 check (requests >= 0)
);

-- Quota days follow Kampala time, so the limit resets at local midnight.
create or replace function public.ai_today()
returns date
language sql
stable
set search_path = public
as $$
  select (now() at time zone 'Africa/Kampala')::date;
$$;

create or replace function public.ai_setting(p_key text)
returns integer
language sql
stable
security definer
set search_path = public
as $$
  select s.value from public.ai_settings s where s.key = p_key;
$$;

-- Admin-only: change a limit. Rejects unknown keys (the table check also enforces this).
create or replace function public.admin_set_ai_setting(p_key text, p_value integer)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  caller text := coalesce(nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'role', '');
begin
  if not (session_user in ('postgres', 'service_role') or caller = 'service_role') then
    raise exception 'not authorised' using errcode = '42501';
  end if;
  update public.ai_settings set value = p_value where key = p_key;
  if not found then
    raise exception 'unknown setting %', p_key;
  end if;
end;
$$;

-- Atomically reserves one AI request for a user. Called only by the ai-assist function.
-- Returns jsonb: {allowed, reason?, remaining?, plus?, max_output_tokens?}.
-- Denied requests leave both counters unchanged.
create or replace function public.ai_consume_quota(p_user uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  today        date := public.ai_today();
  has_plus     boolean := public.plus_active(p_user);
  enabled      integer := coalesce(public.ai_setting('ai_enabled'), 0);
  global_limit integer := coalesce(public.ai_setting('global_daily_requests'), 0);
  user_limit   integer;
  g            integer;
  u            integer;
begin
  if enabled <> 1 then
    return jsonb_build_object('allowed', false, 'reason', 'disabled');
  end if;

  user_limit := case when has_plus then coalesce(public.ai_setting('plus_daily_requests'), 0)
                     else coalesce(public.ai_setting('free_daily_requests'), 0) end;
  if user_limit < 1 then
    return jsonb_build_object('allowed', false, 'reason', 'user_limit', 'plus', has_plus);
  end if;
  if global_limit < 1 then
    return jsonb_build_object('allowed', false, 'reason', 'global_limit');
  end if;

  insert into public.ai_global_usage as gu (usage_day, requests) values (today, 1)
  on conflict (usage_day) do update set requests = gu.requests + 1
    where gu.requests < global_limit
  returning gu.requests into g;
  if g is null then
    return jsonb_build_object('allowed', false, 'reason', 'global_limit');
  end if;

  insert into public.ai_user_usage as uu (user_id, usage_day, requests) values (p_user, today, 1)
  on conflict (user_id, usage_day) do update set requests = uu.requests + 1
    where uu.requests < user_limit
  returning uu.requests into u;
  if u is null then
    update public.ai_global_usage set requests = requests - 1 where usage_day = today;
    return jsonb_build_object('allowed', false, 'reason', 'user_limit', 'plus', has_plus);
  end if;

  return jsonb_build_object(
    'allowed', true,
    'plus', has_plus,
    'remaining', user_limit - u,
    'max_output_tokens', coalesce(public.ai_setting('max_output_tokens'), 256)
  );
end;
$$;

-- Returns one reserved request when the upstream AI call fails, so failures are not charged.
create or replace function public.ai_refund_quota(p_user uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  today date := public.ai_today();
begin
  update public.ai_user_usage
     set requests = greatest(requests - 1, 0)
   where user_id = p_user and usage_day = today and requests > 0;
  if found then
    update public.ai_global_usage
       set requests = greatest(requests - 1, 0)
     where usage_day = today;
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- Row-level security and privileges
-- ---------------------------------------------------------------------------

alter table public.entitlements       enable row level security;
alter table public.ai_settings        enable row level security;
alter table public.ai_user_usage      enable row level security;
alter table public.ai_global_usage    enable row level security;

-- Start from no access for the client roles, then grant only what the apps need.
revoke all on public.entitlements, public.ai_settings, public.ai_user_usage, public.ai_global_usage
  from anon, authenticated;

grant select on public.entitlements, public.ai_user_usage to authenticated;

create policy "users read own entitlements"
  on public.entitlements for select to authenticated
  using (user_id = auth.uid());

create policy "users read own AI usage"
  on public.ai_user_usage for select to authenticated
  using (user_id = auth.uid());

-- Functions: nothing is callable by clients except the user's own read helper.
revoke execute on all functions in schema public from public, anon, authenticated;

grant execute on function public.my_plus_until() to authenticated;

grant execute on function public.admin_grant_plus(uuid, integer, text) to service_role;
grant execute on function public.admin_revoke_plus(uuid, text) to service_role;
grant execute on function public.admin_set_ai_setting(text, integer) to service_role;
grant execute on function public.ai_consume_quota(uuid) to service_role;
grant execute on function public.ai_refund_quota(uuid) to service_role;
grant execute on function public.plus_active(uuid) to service_role;
grant execute on function public.ai_setting(text) to service_role;
grant execute on function public.ai_today() to service_role;
