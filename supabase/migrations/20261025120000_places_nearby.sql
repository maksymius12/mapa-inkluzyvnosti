-- places_nearby + колонки, без яких її (і читання places у застосунку) не створити.
-- Ідемпотентно: можна запускати повторно. Виконати в SQL Editor проєкту Supabase.
--
-- Що з'ясовано (2026-10-25, запити до REST API бази):
--  * у places немає lat, lng (generated) і has_accessible_parking, у checks немає accessible_parking —
--    міграція 20261007120000_accessible_parking.sql впала цілком: вона перестворює places_nearby
--    з p.lat/p.lng, яких немає, і весь скрипт відкотився;
--  * визначення places_nearby у schema.sql не можна створити в принципі: вхідні параметри lat/lng
--    збігаються з колонками lat/lng у RETURNS TABLE («parameter name used more than once»),
--    а ST_MakePoint(lng, lat) у тілі брав би колонки місця замість параметрів.
--
-- Виклик із застосунку (PlaceRepository.placesNearby):
--   POST /rest/v1/rpc/places_nearby  {"lat": <double>, "lng": <double>, "radius_m": <double>}
--   відповідь — масив рядків places (декодується в PlaceReadDto: id, name, address, category,
--   lat, lng, status, source, verified_note, rating, review_count, has_accessible_parking, created_by).

-- 1. Колонки, яких бракує (з schema.sql і 20261007120000_accessible_parking.sql).
alter table public.places
  add column if not exists lat double precision generated always as (ST_Y(location::geometry)) stored;
alter table public.places
  add column if not exists lng double precision generated always as (ST_X(location::geometry)) stored;
alter table public.places
  add column if not exists has_accessible_parking boolean not null default false;
alter table public.checks
  add column if not exists accessible_parking boolean default false;

create index if not exists places_location_idx on public.places using gist (location);

-- 2. Функція. RETURNS SETOF places: назви колонок не конфліктують із параметрами,
-- а відповідь має ті самі поля, що й select * з places. Параметри в тілі — лише через
-- кваліфікацію places_nearby.*, щоб не сплутати з колонками p.lat/p.lng.
-- DROP — на випадок старої версії з іншим типом результату (CREATE OR REPLACE її не замінить).
drop function if exists public.places_nearby(double precision, double precision, double precision);

create or replace function public.places_nearby(lat double precision, lng double precision, radius_m double precision)
returns setof public.places
language sql
stable
set search_path = public, extensions
as $$
  select p.*
  from public.places p
  where p.is_hidden = false
    and ST_DWithin(
      p.location::geography,
      ST_SetSRID(ST_MakePoint(places_nearby.lng, places_nearby.lat), 4326)::geography,
      places_nearby.radius_m
    );
$$;

-- security invoker (за замовчуванням): RLS places діє як і для звичайного select.
grant execute on function public.places_nearby(double precision, double precision, double precision) to anon, authenticated;

-- Оновити кеш схеми PostgREST, щоб rpc/places_nearby і нові колонки стали видимі одразу.
notify pgrst, 'reload schema';
