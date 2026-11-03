-- Блок Г: графік роботи місця.
--
-- Зберігання — jsonb-поле places.opening_hours (простіше за окрему таблицю: графік читається разом із місцем):
--   null                                  — графік не вказано;
--   {"always": true}                      — цілодобово;
--   {"always": false, "days": {"mon": {"open": "09:00", "close": "18:00"}, "sun": null, ...}}
--       ключі mon..sun; null — вихідний; close < open — працює після півночі.
-- Записати / змінити / прибрати графік можуть лише волонтер, учень і адмін — через RPC set_place_hours
-- (пряме UPDATE таблиці places клієнтам закрите міграцією 20261102120000). Мешканець лише бачить.
--
-- Залежить від 20261027130000 (is_reviewer), 20261102120000. Ідемпотентно.

alter table public.places add column if not exists opening_hours jsonb;

create or replace function public.valid_opening_hours(p_hours jsonb)
returns boolean
language plpgsql
immutable
set search_path = public, extensions
as $$
declare
  v_day text;
  v_value jsonb;
begin
  if p_hours is null then
    return true;
  end if;
  if jsonb_typeof(p_hours) <> 'object' or jsonb_typeof(p_hours -> 'always') <> 'boolean' then
    return false;
  end if;
  if (p_hours ->> 'always')::boolean then
    return p_hours - 'always' = '{}'::jsonb;
  end if;
  if jsonb_typeof(p_hours -> 'days') <> 'object' or p_hours - 'always' - 'days' <> '{}'::jsonb then
    return false;
  end if;
  for v_day, v_value in select key, value from jsonb_each(p_hours -> 'days') loop
    if v_day not in ('mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun') then
      return false;
    end if;
    if jsonb_typeof(v_value) = 'null' then
      continue;
    end if;
    if jsonb_typeof(v_value) <> 'object' or v_value - 'open' - 'close' <> '{}'::jsonb
       or coalesce(v_value ->> 'open', '') !~ '^([01][0-9]|2[0-3]):[0-5][0-9]$'
       or coalesce(v_value ->> 'close', '') !~ '^([01][0-9]|2[0-3]):[0-5][0-9]$|^24:00$'
       or v_value ->> 'open' = v_value ->> 'close' then
      return false;
    end if;
  end loop;
  return true;
end;
$$;

alter table public.places drop constraint if exists places_opening_hours_check;
alter table public.places add constraint places_opening_hours_check check (public.valid_opening_hours(opening_hours));

create or replace function public.set_place_hours(p_place_id text, p_hours jsonb)
returns void
language plpgsql
security definer
set search_path = public, extensions
as $$
begin
  if not public.is_reviewer() then
    raise exception using errcode = 'MP403',
      message = 'Графік роботи змінюють волонтер, учень чи адміністратор';
  end if;
  if not public.valid_opening_hours(p_hours) then
    raise exception using errcode = 'MP400', message = 'Перевірте графік: час у форматі ГГ:ХХ, відкриття ≠ закриття';
  end if;
  update public.places set opening_hours = p_hours where id = p_place_id and is_hidden = false;
  if not found then
    raise exception using errcode = 'MP404', message = 'Місце не знайдено';
  end if;
end;
$$;
revoke all on function public.set_place_hours(text, jsonb) from public, anon;
grant execute on function public.set_place_hours(text, jsonb) to authenticated;
