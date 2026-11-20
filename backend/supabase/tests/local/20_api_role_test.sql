-- Runs as the PostgREST API role (authenticator), which is how Supabase's HTTP API connects.
-- session_user is then 'authenticator', so the migration's session_user check is exercised
-- on the same path production uses, not only as the superuser.

\set ON_ERROR_STOP on

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

set session authorization authenticator;

-- A signed-in end user cannot grant or revoke Plus or change limits, even with the API role.
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}', false);
set role authenticated;
select pg_temp.expect_error(
  $q$ select public.admin_grant_plus('00000000-0000-0000-0000-00000000000b', 365, 'self') $q$,
  'permission denied');
select pg_temp.expect_error(
  $q$ select public.admin_set_ai_setting('plus_daily_requests', 100000) $q$,
  'permission denied');
select pg_temp.expect_error(
  $q$ select public.ai_consume_quota('00000000-0000-0000-0000-00000000000b') $q$,
  'permission denied');
reset role;
\echo ok: end users are blocked through the API role

-- The service role (what the ai-assist function uses) can consume quota through the same path.
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-00000000000b","role":"service_role"}', false);
set role service_role;
do $$
declare
  r jsonb;
begin
  r := public.ai_consume_quota('00000000-0000-0000-0000-00000000000b');
  -- the global ceiling is already full from the earlier tests, so the function answers global_limit
  assert r ->> 'reason' = 'global_limit', 'service role reaches the quota function and gets a structured answer';
end $$;
reset role;

-- Note: the function body's own caller check is defence in depth. The EXECUTE revoke is the layer
-- that enforces it for API callers, and the body check is not exercised in isolation here.

reset session authorization;
select 'API ROLE CHECKS PASSED' as result;
