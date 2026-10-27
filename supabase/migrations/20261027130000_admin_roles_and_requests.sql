-- Роль адміністратора, третя мета користування «учень», джерело реєстрації
-- та модерація заявок мешканців на нові локації.
-- Виконати в SQL Editor проєкту Supabase (повністю, одним запуском). Ідемпотентно.

-- ============================================================
-- 1. profiles: роль (admin), мета "учень" (student), referral_source
-- ============================================================
alter table public.profiles add column if not exists role text not null default 'user';
alter table public.profiles drop constraint if exists profiles_role_check;
alter table public.profiles add constraint profiles_role_check check (role in ('user', 'admin'));

alter table public.profiles add column if not exists referral_source text;
alter table public.profiles drop constraint if exists profiles_referral_source_check;
alter table public.profiles add constraint profiles_referral_source_check
  check (referral_source is null or referral_source in ('school', 'friends', 'social_media', 'search', 'other'));

-- handle_new_user_profile: дозволити purpose_role = 'student', зберігати referral_source.
create or replace function public.handle_new_user_profile()
returns trigger
language plpgsql security definer
set search_path = public
as $$
declare
  v_meta jsonb := coalesce(new.raw_user_meta_data, '{}'::jsonb);
  v_role_raw text := v_meta ->> 'purpose_role';
  v_age_raw text := v_meta ->> 'age_group';
  v_referral_raw text := v_meta ->> 'referral_source';
  v_role text;
  v_age text;
  v_name text;
begin
  v_role := case when v_role_raw in ('resident', 'volunteer', 'student') then v_role_raw else 'resident' end;
  v_age := case when v_age_raw in ('student', 'adult') then v_age_raw else 'adult' end;

  v_name := btrim(regexp_replace(
    coalesce(
      nullif(btrim(v_meta ->> 'name'), ''),
      nullif(btrim(v_meta ->> 'full_name'), ''),
      nullif(split_part(coalesce(new.email, ''), '@', 1), ''),
      'Користувач'
    ),
    '[[:cntrl:]]', '', 'g'));
  v_name := left(coalesce(nullif(v_name, ''), 'Користувач'), 80);

  insert into public.profiles (id, name, purpose_role, age_group, roles_confirmed, referral_source)
  values (
    new.id,
    v_name,
    v_role,
    v_age,
    coalesce(v_role_raw in ('resident', 'volunteer', 'student') and v_age_raw in ('student', 'adult'), false),
    nullif(btrim(coalesce(v_referral_raw, '')), '')
  )
  on conflict (id) do nothing;

  return new;
exception when others then
  raise warning 'handle_new_user_profile: % (%)', sqlerrm, sqlstate;
  return new;
end;
$$;

-- Зробити адміном конкретний акаунт — лише якщо він уже зареєстрований
-- (запусти повторно після реєстрації maksymius12@gmail.com у застосунку).
update public.profiles set role = 'admin'
where id = (select id from auth.users where email = 'maksymius12@gmail.com');

-- ============================================================
-- 2. Хелпери прав: is_admin, is_reviewer (волонтер/учень/адмін)
-- ============================================================
create or replace function public.is_admin(p_uid uuid default auth.uid())
returns boolean
language sql stable security definer
set search_path = public, extensions
as $$
  select exists (select 1 from public.profiles where id = p_uid and role = 'admin');
$$;
revoke all on function public.is_admin(uuid) from public, anon;
grant execute on function public.is_admin(uuid) to authenticated;

create or replace function public.is_reviewer(p_uid uuid default auth.uid())
returns boolean
language sql stable security definer
set search_path = public, extensions
as $$
  select exists (
    select 1 from public.profiles
    where id = p_uid and (role = 'admin' or purpose_role in ('volunteer', 'student'))
  );
$$;
revoke all on function public.is_reviewer(uuid) from public, anon;
grant execute on function public.is_reviewer(uuid) to authenticated;

-- Адмін: повний доступ до places (окрім звичайних політик — на додачу).
drop policy if exists "admin reads all places" on public.places;
create policy "admin reads all places" on public.places
  for select to authenticated using (public.is_admin());

drop policy if exists "admin updates all places" on public.places;
create policy "admin updates all places" on public.places
  for update to authenticated using (public.is_admin());

drop policy if exists "admin deletes all places" on public.places;
create policy "admin deletes all places" on public.places
  for delete to authenticated using (public.is_admin());

-- Адмін: бачить і редагує всі профілі (напр. призначити роль/мету іншому користувачу).
drop policy if exists "admin reads all profiles" on public.profiles;
create policy "admin reads all profiles" on public.profiles
  for select to authenticated using (public.is_admin());

drop policy if exists "admin updates all profiles" on public.profiles;
create policy "admin updates all profiles" on public.profiles
  for update to authenticated using (public.is_admin());

-- ============================================================
-- 3. places_before_insert: дозволити вставку "за когось" із SECURITY DEFINER
-- функції approve_place_request нижче (current_user ≠ authenticated/anon усередині
-- такої функції — той самий підхід, що вже використовує place_reports_hide_place).
-- ============================================================
create or replace function public.places_before_insert()
returns trigger
language plpgsql
set search_path = public, extensions
as $$
begin
  if new.source is distinct from 'user' then
    if current_user in ('authenticated', 'anon') then
      new.is_hidden := false;
    end if;
    return new;
  end if;

  if current_user in ('authenticated', 'anon') then
    if auth.uid() is null or new.created_by is distinct from auth.uid() then
      raise exception using errcode = 'MP401', message = 'Увійдіть, щоб додавати місця';
    end if;
  end if;

  new.name := public.normalize_place_text(new.name);
  new.address := public.normalize_place_text(new.address);
  new.verified_note := '';
  perform public.validate_user_place(new.name, new.address, new.category);

  if new.id is null or new.id = '' then
    new.id := 'user-' || gen_random_uuid()::text;
  end if;
  new.status := coalesce(new.status, 'unverified');
  new.is_hidden := false;
  new.created_at := now();
  new.rating := null;
  new.review_count := 0;

  if not public.is_inside_district(new.location) then
    raise exception using errcode = 'MP404', message = 'Місце за межами району';
  end if;

  if current_user in ('authenticated', 'anon') then
    perform pg_advisory_xact_lock(hashtextextended('places_user_limit:' || new.created_by::text, 0));
    if public.user_places_created_last_day(new.created_by) >= 10 then
      raise exception using errcode = 'MP429', message = 'Можна додати не більше 10 місць за добу';
    end if;
  end if;

  return new;
end;
$$;

-- ============================================================
-- 4. Заявки мешканців на нову локацію (place_requests)
-- ============================================================
create table if not exists public.place_requests (
  id uuid primary key default gen_random_uuid(),
  requester_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  address text not null,
  category text not null,
  location geography(Point, 4326) not null,
  note text,
  status text not null default 'pending' check (status in ('pending', 'approved', 'rejected')),
  place_id text references public.places(id) on delete set null,
  reviewed_by uuid references auth.users(id) on delete set null,
  reviewed_at timestamptz,
  reject_reason text,
  created_at timestamptz not null default now()
);
alter table public.place_requests enable row level security;

create index if not exists place_requests_status_idx on public.place_requests (status, created_at desc);

drop policy if exists "requesters read their own requests" on public.place_requests;
create policy "requesters read their own requests" on public.place_requests
  for select to authenticated using (requester_id = auth.uid());

-- Видима всім рецензентам одразу (хто першим розгляне — закриває).
drop policy if exists "reviewers read all requests" on public.place_requests;
create policy "reviewers read all requests" on public.place_requests
  for select to authenticated using (public.is_reviewer());

drop policy if exists "users submit their own requests" on public.place_requests;
create policy "users submit their own requests" on public.place_requests
  for insert to authenticated with check (requester_id = auth.uid());

create or replace function public.submit_place_request(
  p_name text, p_address text, p_category text, p_lat double precision, p_lng double precision, p_note text default null
) returns public.place_requests
language plpgsql security definer
set search_path = public, extensions
as $$
declare
  v_name text := public.normalize_place_text(p_name);
  v_address text := public.normalize_place_text(p_address);
  v_loc geography := ST_SetSRID(ST_MakePoint(p_lng, p_lat), 4326)::geography;
  v_row public.place_requests;
begin
  if auth.uid() is null then
    raise exception using errcode = 'MP401', message = 'Увійдіть, щоб подати заявку';
  end if;
  perform public.validate_user_place(v_name, v_address, p_category);
  if not public.is_inside_district(v_loc) then
    raise exception using errcode = 'MP404', message = 'Місце за межами району';
  end if;

  insert into public.place_requests (requester_id, name, address, category, location, note)
  values (auth.uid(), v_name, v_address, p_category, v_loc, nullif(btrim(coalesce(p_note, '')), ''))
  returning * into v_row;

  return v_row;
end;
$$;
revoke all on function public.submit_place_request(text, text, text, double precision, double precision, text) from public, anon;
grant execute on function public.submit_place_request(text, text, text, double precision, double precision, text) to authenticated;

create or replace function public.approve_place_request(p_request_id uuid)
returns public.places
language plpgsql security definer
set search_path = public, extensions
as $$
declare
  v_req public.place_requests;
  v_place public.places;
begin
  if not public.is_reviewer() then
    raise exception using errcode = 'MP403', message = 'Лише волонтери, учні чи адміністратор можуть розглядати заявки';
  end if;
  select * into v_req from public.place_requests where id = p_request_id and status = 'pending' for update;
  if v_req.id is null then
    raise exception using errcode = 'MP404', message = 'Заявку не знайдено або вже розглянуто';
  end if;

  insert into public.places (id, name, address, category, location, source, status, created_by, is_hidden)
  values ('user-' || gen_random_uuid()::text, v_req.name, v_req.address, v_req.category, v_req.location, 'user', 'unverified', v_req.requester_id, false)
  returning * into v_place;

  update public.place_requests
  set status = 'approved', place_id = v_place.id, reviewed_by = auth.uid(), reviewed_at = now()
  where id = p_request_id;

  return v_place;
end;
$$;
revoke all on function public.approve_place_request(uuid) from public, anon;
grant execute on function public.approve_place_request(uuid) to authenticated;

create or replace function public.reject_place_request(p_request_id uuid, p_reason text default null)
returns void
language plpgsql security definer
set search_path = public, extensions
as $$
begin
  if not public.is_reviewer() then
    raise exception using errcode = 'MP403', message = 'Лише волонтери, учні чи адміністратор можуть розглядати заявки';
  end if;
  update public.place_requests
  set status = 'rejected', reviewed_by = auth.uid(), reviewed_at = now(),
      reject_reason = nullif(btrim(coalesce(p_reason, '')), '')
  where id = p_request_id and status = 'pending';
  if not found then
    raise exception using errcode = 'MP404', message = 'Заявку не знайдено або вже розглянуто';
  end if;
end;
$$;
revoke all on function public.reject_place_request(uuid, text) from public, anon;
grant execute on function public.reject_place_request(uuid, text) to authenticated;

notify pgrst, 'reload schema';

-- Перевірка після запуску:
--   select id, role from public.profiles where role = 'admin'; -- має містити maksymius12@gmail.com (після реєстрації)
