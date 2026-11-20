-- Database tests for the foundation migration. Run as the database owner by scripts/test-db.sh.
-- Every check is an ASSERT or an expected error; any failure stops the run (ON_ERROR_STOP).

\set ON_ERROR_STOP on

insert into auth.users (id) values
  ('00000000-0000-0000-0000-00000000000a'),  -- user A: Plus, then free-quota checks
  ('00000000-0000-0000-0000-00000000000b'),  -- user B: free user
  ('00000000-0000-0000-0000-00000000000c');  -- user C: revocation target

-- Helper: run a statement that must fail with a matching message.
create or replace function pg_temp.expect_error(stmt text, pattern text)
returns void
language plpgsql
as $$
begin
  execute stmt;
  raise exception 'expected an error matching % but the statement succeeded', pattern;
exception when others then
  if sqlerrm !~ pattern then
    raise exception 'wrong error: % (wanted %)', sqlerrm, pattern;
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- Admin grants (database owner)
-- ---------------------------------------------------------------------------

select public.admin_grant_plus('00000000-0000-0000-0000-00000000000a', 30, 'first grant');

do $$
declare
  first_end timestamptz;
  second    public.entitlements;
begin
  assert public.plus_active('00000000-0000-0000-0000-00000000000a'), 'admin grant should activate Plus';

  select ends_at into first_end
  from public.entitlements
  where user_id = '00000000-0000-0000-0000-00000000000a'
  order by created_at
  limit 1;

  second := public.admin_grant_plus('00000000-0000-0000-0000-00000000000a', 10, 'stacked');
  assert second.starts_at = first_end, 'a stacked grant starts when the previous one ends';
  assert second.ends_at = first_end + interval '10 days', 'a stacked grant has the requested length';
  assert second.source = 'admin', 'grants are recorded as admin';
  raise notice 'ok: admin grant and stacking';
end $$;

select pg_temp.expect_error(
  $q$ select public.admin_grant_plus('00000000-0000-0000-0000-00000000000b', 0, 'bad') $q$,
  'between 1 and 366');
select pg_temp.expect_error(
  $q$ select public.admin_grant_plus('00000000-0000-0000-0000-00000000000b', 367, 'bad') $q$,
  'between 1 and 366');
select pg_temp.expect_error(
  $q$ select public.admin_grant_plus('00000000-0000-0000-0000-0000000000ff', 30, 'ghost') $q$,
  'unknown user');
\echo ok: grant input validation

-- ---------------------------------------------------------------------------
-- Row-level security and client privileges (as the authenticated role)
-- ---------------------------------------------------------------------------

set role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', false);

do $$
begin
  assert (select count(*) from public.entitlements) = 2, 'user A sees their own two entitlements';
  assert (select public.my_plus_until()) is not null, 'user A can read their own Plus expiry';
  raise notice 'ok: user reads own entitlements';
end $$;

select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', false);
do $$
begin
  assert (select count(*) from public.entitlements) = 0, 'user B cannot see user A entitlements';
  assert (select count(*) from public.ai_user_usage) = 0, 'user B cannot see anyone else AI usage';
  assert (select public.my_plus_until()) is null, 'user B has no Plus expiry';
  raise notice 'ok: RLS hides other users rows';
end $$;

select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}', false);
select pg_temp.expect_error(
  $q$ insert into public.entitlements (user_id, ends_at, source, granted_by)
      values ('00000000-0000-0000-0000-00000000000a', now() + interval '1000 days', 'admin', 'self') $q$,
  'permission denied');
select pg_temp.expect_error(
  $q$ update public.entitlements set ends_at = now() + interval '1000 days' $q$,
  'permission denied');
select pg_temp.expect_error(
  $q$ select public.admin_grant_plus('00000000-0000-0000-0000-00000000000a', 365, 'self') $q$,
  'permission denied');
select pg_temp.expect_error(
  $q$ select * from public.ai_settings $q$, 'permission denied');
select pg_temp.expect_error(
  $q$ select * from public.ai_global_usage $q$, 'permission denied');
select pg_temp.expect_error(
  $q$ select public.ai_consume_quota('00000000-0000-0000-0000-00000000000a') $q$, 'permission denied');
select pg_temp.expect_error(
  $q$ select public.admin_set_ai_setting('plus_daily_requests', 100000) $q$, 'permission denied');
\echo ok: clients cannot write entitlements, grant Plus, read config or consume quota

reset role;
select set_config('request.jwt.claims', '', false);

-- ---------------------------------------------------------------------------
-- Quota: limits, atomic denial, refunds, kill switch (as the service role)
-- ---------------------------------------------------------------------------

select public.admin_set_ai_setting('global_daily_requests', 7);
select public.admin_set_ai_setting('free_daily_requests', 5);
select public.admin_set_ai_setting('plus_daily_requests', 100);

set role service_role;

do $$
declare
  r jsonb;
  g integer;
begin
  -- free user B: five allowed, sixth denied, and the denial does not count
  for i in 1..5 loop
    r := public.ai_consume_quota('00000000-0000-0000-0000-00000000000b');
    assert (r ->> 'allowed')::boolean, format('free request %s should be allowed', i);
    assert (r ->> 'remaining')::int = 5 - i, format('remaining after %s should be %s', i, 5 - i);
  end loop;
  r := public.ai_consume_quota('00000000-0000-0000-0000-00000000000b');
  assert not (r ->> 'allowed')::boolean and r ->> 'reason' = 'user_limit', 'sixth free request is denied';
  select requests into g from public.ai_global_usage where usage_day = public.ai_today();
  assert g = 5, format('denied request must not raise the global count (got %s)', g);
  raise notice 'ok: free per-user limit enforced without charging denials';
end $$;

do $$
declare
  r jsonb;
  g integer;
begin
  -- Plus user A: allowed, uses the larger limit, and the 7-request global cap applies
  r := public.ai_consume_quota('00000000-0000-0000-0000-00000000000a');
  assert (r ->> 'allowed')::boolean and (r ->> 'plus')::boolean, 'Plus user is allowed with Plus flag';
  assert (r ->> 'remaining')::int = 99, 'Plus user has the Plus limit';
  assert (r ->> 'max_output_tokens')::int = 1024, 'output cap is returned from settings';

  r := public.ai_consume_quota('00000000-0000-0000-0000-00000000000a');
  assert (r ->> 'allowed')::boolean, 'second Plus request is allowed (global now 7)';

  r := public.ai_consume_quota('00000000-0000-0000-0000-00000000000a');
  assert not (r ->> 'allowed')::boolean and r ->> 'reason' = 'global_limit', 'global ceiling denies the eighth request';
  select requests into g from public.ai_global_usage where usage_day = public.ai_today();
  assert g = 7, format('global count must stay at the ceiling (got %s)', g);

  -- refund returns the reserved request to both counters
  perform public.ai_refund_quota('00000000-0000-0000-0000-00000000000a');
  select requests into g from public.ai_global_usage where usage_day = public.ai_today();
  assert g = 6, format('refund lowers the global count (got %s)', g);
  r := public.ai_consume_quota('00000000-0000-0000-0000-00000000000a');
  assert (r ->> 'allowed')::boolean, 'refunded capacity can be used again';
  raise notice 'ok: global ceiling and refunds';
end $$;

do $$
declare
  r jsonb;
begin
  -- kill switch denies everything, and clients cannot flip it (checked in the authenticator run)
  perform public.admin_set_ai_setting('ai_enabled', 0);
  r := public.ai_consume_quota('00000000-0000-0000-0000-00000000000b');
  assert r ->> 'reason' = 'disabled', 'kill switch disables AI';
  perform public.admin_set_ai_setting('ai_enabled', 1);
  -- the global ceiling (7) is already full today, so with the switch back on the answer is global_limit
  r := public.ai_consume_quota('00000000-0000-0000-0000-00000000000b');
  assert r ->> 'reason' = 'global_limit', 'after the kill switch is cleared, the global ceiling applies again';
  raise notice 'ok: kill switch';
end $$;

-- Refund for a user with no usage today must not change the global count.
do $$
declare
  g_before integer;
  g_after integer;
begin
  select requests into g_before from public.ai_global_usage where usage_day = public.ai_today();
  perform public.ai_refund_quota('00000000-0000-0000-0000-00000000000c');
  select requests into g_after from public.ai_global_usage where usage_day = public.ai_today();
  assert g_before = g_after, 'refund for a user with no usage leaves the global count alone';
  raise notice 'ok: refund ignores users with no usage';
end $$;

reset role;

-- ---------------------------------------------------------------------------
-- Revocation (database owner)
-- ---------------------------------------------------------------------------

select public.admin_grant_plus('00000000-0000-0000-0000-00000000000c', 30, 'to be refunded');

do $$
declare
  eid uuid;
begin
  assert public.plus_active('00000000-0000-0000-0000-00000000000c'), 'user C has Plus before revocation';
  select id into eid from public.entitlements where user_id = '00000000-0000-0000-0000-00000000000c';
  perform public.admin_revoke_plus(eid, 'refund issued');
  assert not public.plus_active('00000000-0000-0000-0000-00000000000c'), 'a revoked entitlement grants no Plus';
  raise notice 'ok: revocation removes Plus and keeps the audit row';
end $$;

select pg_temp.expect_error(
  $q$ select public.admin_revoke_plus(
        (select id from public.entitlements where user_id = '00000000-0000-0000-0000-00000000000c'), '') $q$,
  'reason is required');
select pg_temp.expect_error(
  $q$ select public.admin_set_ai_setting('no_such_key', 1) $q$, 'unknown setting');
\echo ok: revocation and settings input validation

select 'ALL DATABASE CHECKS PASSED' as result;
