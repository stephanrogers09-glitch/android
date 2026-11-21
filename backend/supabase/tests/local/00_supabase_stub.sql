-- Local-only stand-in for the parts of Supabase the migration depends on.
-- Not applied to a real Supabase project. Mirrors the roles, default privileges, auth.users
-- and auth.uid() that Supabase provides, so RLS and grants can be tested on plain Postgres.

do $$
begin
  if not exists (select from pg_roles where rolname = 'anon') then
    create role anon nologin noinherit;
  end if;
  if not exists (select from pg_roles where rolname = 'authenticated') then
    create role authenticated nologin noinherit;
  end if;
  if not exists (select from pg_roles where rolname = 'service_role') then
    create role service_role nologin noinherit bypassrls;
  end if;
  if not exists (select from pg_roles where rolname = 'authenticator') then
    create role authenticator login noinherit;
  end if;
end $$;

grant anon, authenticated, service_role to authenticator;

create schema if not exists public;
create schema if not exists auth;
grant usage on schema public, auth to anon, authenticated, service_role;

create table if not exists auth.users (id uuid primary key);

create or replace function auth.uid()
returns uuid
language sql
stable
as $$
  select nullif(
    coalesce(nullif(current_setting('request.jwt.claims', true), ''), '{}')::jsonb ->> 'sub',
    ''
  )::uuid;
$$;

-- Supabase grants new public tables to the API roles by default; the migration then revokes
-- what clients should not have. Reproducing the default makes that revoke meaningful.
alter default privileges in schema public grant all on tables to anon, authenticated, service_role;
alter default privileges in schema public grant all on functions to public;
