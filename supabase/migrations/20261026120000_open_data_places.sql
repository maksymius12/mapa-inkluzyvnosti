-- Місця з відкритих даних («Мапа безбар'єрності», Мінрозвитку / ЛУН Місто, data.gov.ua, CC BY).
-- Ідемпотентно. Виконати в SQL Editor ДО файлів із supabase/imports/.
-- Потрібна попередня міграція 20261025120000_places_nearby.sql (колонки lat/lng).
--
-- source = 'open_data', created_by = null. Звичайний користувач не може їх створити, змінити зміст
-- (назва/адреса/категорія/позиція/джерело) чи видалити, але може пройти перевірку за 7 критеріями
-- (оновлює status / has_accessible_parking) і додати фото — як і для OSM-точок.

-- 1. Колонки й унікальність (source, external_id) для ON CONFLICT в імпорті.
alter table public.places add column if not exists external_id text;
alter table public.places add column if not exists attribution text;

create unique index if not exists places_source_external_id_key
  on public.places (source, external_id);

-- 2. Схожість назв для пропуску дублів (≤30 м). Дзеркало PlaceInputRules.isSimilarName у застосунку:
-- збіг після нормалізації, одна назва містить іншу (≥4 символи) або Левенштейн ≤ чверті довжини.
create extension if not exists fuzzystrmatch with schema extensions;

create or replace function public.place_name_key(p_name text)
returns text
language sql immutable
as $$
  select btrim(regexp_replace(
    -- Лише розділові знаки (ASCII-клас + типографські лапки й №); літери будь-якої абетки не чіпаємо.
    regexp_replace(replace(replace(lower(coalesce(p_name, '')), 'ё', 'е'), 'ʼ', ''''), '[[:punct:]«»“”„‘’`№–—]', ' ', 'g'),
    '\s+', ' ', 'g'));
$$;

create or replace function public.place_names_similar(p_a text, p_b text)
returns boolean
language plpgsql immutable
set search_path = public, extensions
as $$
declare
  x text := public.place_name_key(p_a);
  y text := public.place_name_key(p_b);
begin
  if x = '' or y = '' then
    return false;
  end if;
  if x = y then
    return true;
  end if;
  if least(char_length(x), char_length(y)) >= 4 and (strpos(x, y) > 0 or strpos(y, x) > 0) then
    return true;
  end if;
  if greatest(char_length(x), char_length(y)) > 255 then
    return false; -- обмеження levenshtein()
  end if;
  return levenshtein(x, y) <= greatest(1, greatest(char_length(x), char_length(y)) / 4);
end;
$$;

-- 3. Захист змісту: доповнює places_before_update із 20261023120000_user_places.sql
-- (той самий код + блок для open_data і колонок external_id/attribution).
create or replace function public.places_before_update()
returns trigger
language plpgsql
set search_path = public, extensions
as $$
begin
  if current_user in ('authenticated', 'anon') then
    if new.id is distinct from old.id
       or new.source is distinct from old.source
       or new.created_by is distinct from old.created_by
       or new.created_at is distinct from old.created_at
       or new.is_hidden is distinct from old.is_hidden
       or new.external_id is distinct from old.external_id
       or new.attribution is distinct from old.attribution then
      raise exception using errcode = 'MP403', message = 'Ці поля змінювати не можна';
    end if;

    if old.source = 'user'
       and old.created_by is distinct from auth.uid()
       and (new.name is distinct from old.name
            or new.address is distinct from old.address
            or new.category is distinct from old.category
            or new.location is distinct from old.location) then
      raise exception using errcode = 'MP403', message = 'Змінювати можна лише власні місця';
    end if;

    -- Відкриті дані: зміст не змінює ніхто з клієнтів (перевірка за 7 критеріями змінює лише статус).
    if old.source = 'open_data'
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

-- Вставка open_data з клієнта неможлива: політики INSERT дозволяють лише source = 'user' (власні)
-- та 'osm'. Видалення — лише автор власних 'user'. Імпорт виконується в SQL Editor (роль postgres).

notify pgrst, 'reload schema';
