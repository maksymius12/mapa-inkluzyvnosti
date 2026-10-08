-- Паркування для МГН: додатковий атрибут закладу та поле в перевірці.
-- У розрахунок статусу за 7 критеріями НЕ входить.
-- Виконати в SQL Editor проєкту Supabase.

alter table places add column if not exists has_accessible_parking boolean not null default false;
alter table checks add column if not exists accessible_parking boolean default false;

-- Повертає нове поле has_accessible_parking: змінюється тип результату, тому функцію перестворюємо.
drop function if exists places_nearby(float, float, float);

create function places_nearby(lat float, lng float, radius_m float)
returns table (
  id text, name text, address text, category text,
  lat double precision, lng double precision,
  status text, source text, verified_note text,
  rating numeric, review_count int,
  has_accessible_parking boolean
) as $$
  select p.id, p.name, p.address, p.category, p.lat, p.lng,
         p.status, p.source, p.verified_note, p.rating, p.review_count,
         p.has_accessible_parking
  from places p
  where ST_DWithin(p.location, ST_MakePoint(lng, lat)::geography, radius_m);
$$ language sql stable;
