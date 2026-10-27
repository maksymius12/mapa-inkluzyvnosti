-- Виправлення до 20261027130000_admin_roles_and_requests.sql і 20261027140000_admin_full_access_bypass.sql.
-- Ідемпотентно. Виконати одним запуском у SQL Editor ПІСЛЯ них (і після 20261026120000_open_data_places.sql,
-- якщо її запускатимете: вона перезаписує places_before_update без винятку для адміна).
--
-- 1) Ескалація привілеїв: політика "users update their own profile" дозволяє змінювати будь-яку колонку
--    свого рядка, тож будь-хто міг PATCH-ем поставити собі profiles.role = 'admin'.
-- 2) places_before_insert у 20261027130000 перестав примусово ставити status = 'unverified' і серверний id
--    для клієнтських вставок — клієнт міг створити місце одразу «доступним» або з чужим id ('osm-…').
-- 3) places_before_update у 20261027140000 повернув стару версію без захисту відкритих даних
--    (source = 'open_data', колонки external_id/attribution) з 20261026120000; тут — обидва, плюс адмін
--    може знімати/ставити is_hidden (модерація скарг).
-- 4) Пряма вставка в place_requests в обхід submit_place_request (без перевірки категорії, меж району):
--    невідома категорія ламала розбір усієї черги в застосунку. Тепер лише через RPC.

-- 1. profiles.role змінює лише адміністратор (або серверні функції / SQL Editor).
create or replace function public.profiles_protect_role()
returns trigger
language plpgsql
set search_path = public, extensions
as $$
begin
  if current_user in ('authenticated', 'anon') and not public.is_admin() then
    if tg_op = 'INSERT' then
      new.role := 'user';
    elsif new.role is distinct from old.role then
      raise exception using errcode = 'MP403', message = 'Змінювати роль може лише адміністратор';
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists profiles_protect_role on public.profiles;
create trigger profiles_protect_role
  before insert or update on public.profiles
  for each row execute function public.profiles_protect_role();

-- 2. Вставка місць: як у 20261027130000 (approve_place_request вставляє «за мешканця»),
--    але для клієнтських вставок знову примусові id, статус і ліміт.
create or replace function public.places_before_insert()
returns trigger
language plpgsql
set search_path = public, extensions
as $$
declare
  v_client boolean := current_user in ('authenticated', 'anon');
begin
  if new.source is distinct from 'user' then
    if v_client then
      new.is_hidden := false;
    end if;
    return new;
  end if;

  if v_client and (auth.uid() is null or new.created_by is distinct from auth.uid()) then
    raise exception using errcode = 'MP401', message = 'Увійдіть, щоб додавати місця';
  end if;

  new.name := public.normalize_place_text(new.name);
  new.address := public.normalize_place_text(new.address);
  new.verified_note := '';
  perform public.validate_user_place(new.name, new.address, new.category);

  if v_client or new.id is null or new.id = '' then
    new.id := 'user-' || gen_random_uuid()::text;
  end if;
  -- Нове місце завжди «не перевірено», доки хтось не пройде перевірку за 7 критеріями.
  new.status := 'unverified';
  new.is_hidden := false;
  new.created_at := now();
  new.rating := null;
  new.review_count := 0;

  if not public.is_inside_district(new.location) then
    raise exception using errcode = 'MP404', message = 'Місце за межами району';
  end if;

  if v_client then
    perform pg_advisory_xact_lock(hashtextextended('places_user_limit:' || new.created_by::text, 0));
    if public.user_places_created_last_day(new.created_by) >= 10 then
      raise exception using errcode = 'MP429', message = 'Можна додати не більше 10 місць за добу';
    end if;
  end if;

  return new;
end;
$$;

-- 3. Оновлення місць: захист змісту + відкритих даних (20261026) + повний доступ адміна (20261027140000).
alter table public.places add column if not exists external_id text;
alter table public.places add column if not exists attribution text;

create or replace function public.places_before_update()
returns trigger
language plpgsql
set search_path = public, extensions
as $$
declare
  v_admin boolean;
begin
  if current_user in ('authenticated', 'anon') then
    v_admin := public.is_admin();

    if new.id is distinct from old.id
       or new.source is distinct from old.source
       or new.created_by is distinct from old.created_by
       or new.created_at is distinct from old.created_at
       or new.external_id is distinct from old.external_id
       or new.attribution is distinct from old.attribution
       or (new.is_hidden is distinct from old.is_hidden and not v_admin) then
      raise exception using errcode = 'MP403', message = 'Ці поля змінювати не можна';
    end if;

    if old.source = 'user'
       and old.created_by is distinct from auth.uid()
       and not v_admin
       and (new.name is distinct from old.name
            or new.address is distinct from old.address
            or new.category is distinct from old.category
            or new.location is distinct from old.location) then
      raise exception using errcode = 'MP403', message = 'Змінювати можна лише власні місця';
    end if;

    if old.source = 'open_data'
       and not v_admin
       and (new.name is distinct from old.name
            or new.address is distinct from old.address
            or new.category is distinct from old.category
            or new.location is distinct from old.location
            or new.verified_note is distinct from old.verified_note) then
      raise exception using errcode = 'MP403', message = 'Місця з відкритих даних змінювати не можна';
    end if;
  end if;

  if new.source = 'user' then
    new.name := public.normalize_place_text(new.name);
    new.address := public.normalize_place_text(new.address);
    new.verified_note := coalesce(new.verified_note, '');
    perform public.validate_user_place(new.name, new.address, new.category);
    if new.location is distinct from old.location and not public.is_inside_district(new.location) then
      raise exception using errcode = 'MP404', message = 'Місце за межами району';
    end if;
  end if;

  return new;
end;
$$;

-- 4. Заявки — лише через submit_place_request (security definer, з перевірками).
drop policy if exists "users submit their own requests" on public.place_requests;

notify pgrst, 'reload schema';

-- Перевірка після запуску:
--   select proname from pg_proc where proname in ('profiles_protect_role', 'places_before_update', 'places_before_insert');
--   -- від імені звичайного користувача PATCH profiles set role='admin' має повернути помилку MP403.
