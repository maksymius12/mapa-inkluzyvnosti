-- Відгуки до місць (як у Google Maps): оцінка 1–5 зірок + текстовий коментар.
--
--   * place_reviews — один відгук на місце від одного користувача (unique (place_id, user_id));
--     повторний «Залишити відгук» у застосунку редагує наявний.
--   * places.avg_rating / places.reviews_count — оновлює тригер після insert/update/delete у place_reviews,
--     щоб не рахувати середнє на льоту. Старі places.rating / review_count і таблиця reviews зі schema.sql
--     порожні й ніде не заповнювались — їх не чіпаємо.
--   * author_name — ім'я автора на момент відгуку. Профілі (profiles) читає лише власник/адмін, тож
--     показати автора в публічному списку без цієї колонки неможливо. Ставить сервер, не клієнт.
--
-- Права — той самий патерн, що й у місцях/заявках: RLS + public.is_admin() (20261027130000).
-- Залежить від 20261027130000_admin_roles_and_requests.sql. Ідемпотентно. Виконати одним запуском у SQL Editor.

-- 1. Таблиця відгуків.
create table if not exists public.place_reviews (
  id uuid primary key default gen_random_uuid(),
  place_id text not null references public.places(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  rating smallint not null check (rating between 1 and 5),
  comment text check (comment is null or char_length(comment) <= 1000),
  author_name text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (place_id, user_id)
);
create index if not exists place_reviews_place_created_idx on public.place_reviews (place_id, created_at desc);

alter table public.place_reviews enable row level security;

drop policy if exists "place reviews are publicly readable" on public.place_reviews;
create policy "place reviews are publicly readable" on public.place_reviews
  for select using (true);

drop policy if exists "users add their own review" on public.place_reviews;
create policy "users add their own review" on public.place_reviews
  for insert to authenticated with check (user_id = auth.uid());

drop policy if exists "owner or admin updates review" on public.place_reviews;
create policy "owner or admin updates review" on public.place_reviews
  for update to authenticated
  using (user_id = auth.uid() or public.is_admin())
  with check (user_id = auth.uid() or public.is_admin());

drop policy if exists "owner or admin deletes review" on public.place_reviews;
create policy "owner or admin deletes review" on public.place_reviews
  for delete to authenticated using (user_id = auth.uid() or public.is_admin());

-- 2. Серверні поля: дати, ім'я автора, нормалізований коментар; місце й автора змінити не можна.
create or replace function public.place_reviews_before_write()
returns trigger
language plpgsql security definer
set search_path = public, extensions
as $$
begin
  new.comment := nullif(btrim(coalesce(new.comment, '')), '');
  if tg_op = 'INSERT' then
    new.created_at := now();
    new.updated_at := now();
    new.author_name := coalesce(
      (select nullif(btrim(p.name), '') from public.profiles p where p.id = new.user_id),
      'Користувач'
    );
  else
    if new.place_id is distinct from old.place_id or new.user_id is distinct from old.user_id then
      raise exception using errcode = 'MP403', message = 'Ці поля змінювати не можна';
    end if;
    new.created_at := old.created_at;
    new.author_name := old.author_name;
    new.updated_at := now();
  end if;
  return new;
end;
$$;

drop trigger if exists place_reviews_before_write on public.place_reviews;
create trigger place_reviews_before_write
  before insert or update on public.place_reviews
  for each row execute function public.place_reviews_before_write();

-- 3. Середній рейтинг і кількість відгуків на місці.
alter table public.places add column if not exists avg_rating numeric(3, 2);
alter table public.places add column if not exists reviews_count int not null default 0;

create or replace function public.refresh_place_review_stats(p_place_id text)
returns void
language sql security definer
set search_path = public, extensions
as $$
  update public.places p
  set avg_rating = s.avg_rating, reviews_count = s.cnt
  from (
    select round(avg(r.rating)::numeric, 2) as avg_rating, count(*)::int as cnt
    from public.place_reviews r where r.place_id = p_place_id
  ) s
  where p.id = p_place_id;
$$;
revoke all on function public.refresh_place_review_stats(text) from public, anon, authenticated;

create or replace function public.place_reviews_after_change()
returns trigger
language plpgsql security definer
set search_path = public, extensions
as $$
begin
  if tg_op in ('UPDATE', 'DELETE') then
    perform public.refresh_place_review_stats(old.place_id);
  end if;
  if tg_op in ('INSERT', 'UPDATE') and (tg_op = 'INSERT' or new.place_id is distinct from old.place_id) then
    perform public.refresh_place_review_stats(new.place_id);
  end if;
  return null;
end;
$$;

drop trigger if exists place_reviews_after_change on public.place_reviews;
create trigger place_reviews_after_change
  after insert or update or delete on public.place_reviews
  for each row execute function public.place_reviews_after_change();

-- Клієнт не може підробити рейтинг місця: avg_rating / reviews_count змінює лише тригер вище.
create or replace function public.places_protect_review_stats()
returns trigger
language plpgsql
set search_path = public, extensions
as $$
begin
  if current_user in ('authenticated', 'anon') then
    if tg_op = 'INSERT' then
      new.avg_rating := null;
      new.reviews_count := 0;
    else
      new.avg_rating := old.avg_rating;
      new.reviews_count := old.reviews_count;
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists places_protect_review_stats on public.places;
create trigger places_protect_review_stats
  before insert or update on public.places
  for each row execute function public.places_protect_review_stats();

notify pgrst, 'reload schema';

-- Перевірка після запуску:
--   select policyname, cmd from pg_policies where tablename = 'place_reviews';
--   -- після першого відгуку в застосунку:
--   select id, avg_rating, reviews_count from public.places where reviews_count > 0;
