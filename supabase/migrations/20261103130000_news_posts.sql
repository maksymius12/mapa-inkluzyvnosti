-- Блок Е: стрічка «Новини» (оголошення). Не чат: коментарів і повідомлень між користувачами немає.
--
-- Публікує, змінює й видаляє лише адміністратор (волонтерів / учнів свідомо не додано: серед учнів є
-- підлітки, а публічні дописи без модерації — ризик). Читають усі, зокрема гості.
-- Фото допису — у бакеті place-photos у теці автора (<uid>/news/…): завантаження дозволяє наявна політика
-- "reviewers upload place photos" (адмін — рецензент), видалення — photos_storage_delete_own.
--
-- Залежить від 20261027130000 (is_admin). Ідемпотентно.

create table if not exists public.news_posts (
  id uuid primary key default gen_random_uuid(),
  author_id uuid references auth.users(id) on delete set null,
  title text not null check (char_length(btrim(title)) between 3 and 120),
  body text not null check (char_length(btrim(body)) between 1 and 5000),
  photo_url text check (photo_url is null or char_length(photo_url) <= 1000),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists news_posts_created_idx on public.news_posts (created_at desc);

alter table public.news_posts enable row level security;

drop policy if exists "news are publicly readable" on public.news_posts;
create policy "news are publicly readable" on public.news_posts
  for select using (true);

drop policy if exists "admin publishes news" on public.news_posts;
create policy "admin publishes news" on public.news_posts
  for insert to authenticated
  with check (public.is_admin() and author_id = auth.uid());

drop policy if exists "admin updates news" on public.news_posts;
create policy "admin updates news" on public.news_posts
  for update to authenticated
  using (public.is_admin()) with check (public.is_admin());

drop policy if exists "admin deletes news" on public.news_posts;
create policy "admin deletes news" on public.news_posts
  for delete to authenticated using (public.is_admin());

create or replace function public.news_posts_before_write()
returns trigger
language plpgsql
set search_path = public, extensions
as $$
begin
  new.title := btrim(new.title);
  new.body := btrim(new.body);
  if tg_op = 'INSERT' then
    new.created_at := now();
  else
    new.author_id := old.author_id;
    new.created_at := old.created_at;
  end if;
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists news_posts_before_write on public.news_posts;
create trigger news_posts_before_write
  before insert or update on public.news_posts
  for each row execute function public.news_posts_before_write();
