-- Мапа Інклюзивності — схема бази даних Supabase.
-- Виконати повністю в SQL Editor проєкту Supabase (одноразово).

create extension if not exists postgis;

create table places (
  id text primary key,
  name text not null,
  address text,
  category text not null,
  location geography(point, 4326) not null,
  -- Generated-колонки: Kotlin-сторона читає lat/lng напряму через Postgrest select(),
  -- не парсячи бінарний/geojson-формат geography. При insert/upsert передається
  -- лише `location` як WKT-текст ("POINT(lng lat)") — Postgres сам приводить його
  -- до geography, а lat/lng обчислюються автоматично.
  lat double precision generated always as (ST_Y(location::geometry)) stored,
  lng double precision generated always as (ST_X(location::geometry)) stored,
  status text not null default 'unverified',
  source text not null default 'community',
  verified_note text,
  rating numeric,
  review_count int default 0,
  has_accessible_parking boolean not null default false, -- додатково, не входить у розрахунок статусу
  created_at timestamptz default now()
);
create index places_location_idx on places using gist (location);

create table checks (
  id uuid primary key default gen_random_uuid(),
  place_id text references places(id),
  user_id uuid references auth.users(id),
  ramp boolean, door_width boolean, threshold boolean,
  elevator boolean, toilet boolean, tactile boolean, staff_assistance boolean,
  accessible_parking boolean default false, -- додатково, не входить у розрахунок статусу
  comment text,
  created_at timestamptz default now()
);
create index checks_place_id_created_at_idx on checks (place_id, created_at desc);

create table reviews (
  id uuid primary key default gen_random_uuid(),
  place_id text references places(id),
  user_id uuid references auth.users(id),
  rating int check (rating between 1 and 5),
  text text,
  created_at timestamptz default now()
);

create table favorites (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users(id),
  place_id text references places(id),
  created_at timestamptz default now()
);
create unique index favorites_user_place_unique on favorites (user_id, place_id);

create table profiles (
  id uuid primary key references auth.users(id),
  name text,
  purpose_role text not null default 'resident',   -- 'resident' | 'volunteer'
  age_group text not null default 'adult',         -- 'student' | 'adult'
  points int default 0,
  checks_count int default 0
);

create table place_photos (
  id uuid primary key default gen_random_uuid(),
  place_id text references places(id),
  user_id uuid references auth.users(id),
  url text not null,
  ai_title text,
  ai_suggestions jsonb,
  ai_suggested_status text,
  created_at timestamptz default now()
);
create index place_photos_place_id_created_at_idx on place_photos (place_id, created_at desc);

-- ============================================================
-- RPC: геопошук місць у радіусі (метри). Повертає ті самі поля,
-- що й звичайний select("*") на places — Kotlin-сторона декодує
-- обидва варіанти в один PlaceReadDto.
-- ============================================================
create or replace function places_nearby(lat float, lng float, radius_m float)
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

-- ============================================================
-- RPC: гейміфікація — виконується після кожної успішної швидкої
-- перевірки, яку подає користувач з age_group = 'student'.
-- ============================================================
create or replace function increment_profile_progress(p_user_id uuid, p_points_delta int)
returns void as $$
  update profiles
  set points = points + p_points_delta,
      checks_count = checks_count + 1
  where id = p_user_id;
$$ language sql volatile security definer;

-- ============================================================
-- Row Level Security. Мапа доступності — публічні дані на читання;
-- запис дозволений лише автентифікованим користувачам і лише для
-- власних рядків (user_id = auth.uid()).
-- ============================================================
alter table places enable row level security;
alter table checks enable row level security;
alter table reviews enable row level security;
alter table favorites enable row level security;
alter table profiles enable row level security;
alter table place_photos enable row level security;

create policy "places are publicly readable" on places
  for select using (true);
create policy "authenticated users can add places" on places
  for insert to authenticated with check (true);
create policy "authenticated users can update places" on places
  for update to authenticated using (true);

create policy "checks are publicly readable" on checks
  for select using (true);
create policy "users can add their own checks" on checks
  for insert to authenticated with check (auth.uid() = user_id);

create policy "reviews are publicly readable" on reviews
  for select using (true);
create policy "users can add their own reviews" on reviews
  for insert to authenticated with check (auth.uid() = user_id);

create policy "users manage their own favorites" on favorites
  for all to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "users read their own profile" on profiles
  for select to authenticated using (auth.uid() = id);
create policy "users create their own profile" on profiles
  for insert to authenticated with check (auth.uid() = id);
create policy "users update their own profile" on profiles
  for update to authenticated using (auth.uid() = id);

create policy "place photos are publicly readable" on place_photos
  for select using (true);
create policy "users can add their own place photos" on place_photos
  for insert to authenticated with check (auth.uid() = user_id);

-- ============================================================
-- Storage: bucket для фото місць.
-- Найпростіше створити через Dashboard → Storage → New bucket:
--   name: place-photos, Public bucket: увімкнено (публічне читання).
-- Або еквівалент через SQL (виконати окремо, після ввімкнення storage):
-- ============================================================
insert into storage.buckets (id, name, public)
values ('place-photos', 'place-photos', true)
on conflict (id) do nothing;

create policy "place photos are publicly readable in storage" on storage.objects
  for select using (bucket_id = 'place-photos');
create policy "authenticated users can upload place photos" on storage.objects
  for insert to authenticated with check (bucket_id = 'place-photos');
