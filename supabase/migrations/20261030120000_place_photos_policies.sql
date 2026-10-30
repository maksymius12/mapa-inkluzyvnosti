-- Повертає політики для фото місць, яких немає в робочій базі.
--
-- Симптом: «Додати фото» в застосунку падає з помилкою. Відтворено запитами від імені звичайного
-- залогіненого користувача (так само, як робить застосунок):
--   * POST /storage/v1/object/place-photos/...  → 403 "new row violates row-level security policy";
--   * POST /rest/v1/place_photos (user_id = auth.uid()) → 42501 "new row violates row-level security policy".
-- Політики з supabase/schema.sql у робочій базі відсутні (жодна міграція їх не створювала),
-- тож ні файл, ні рядок у place_photos записати неможливо — до AI-аналізу справа не доходить.
--
-- Ідемпотентно. Виконати одним запуском у SQL Editor.

-- 1. Bucket place-photos — публічний на читання (фото показуються за public URL).
insert into storage.buckets (id, name, public) values ('place-photos', 'place-photos', true)
on conflict (id) do update set public = true;

drop policy if exists "place photos are publicly readable in storage" on storage.objects;
create policy "place photos are publicly readable in storage" on storage.objects
  for select using (bucket_id = 'place-photos');

drop policy if exists "authenticated users can upload place photos" on storage.objects;
create policy "authenticated users can upload place photos" on storage.objects
  for insert to authenticated with check (bucket_id = 'place-photos');

-- 2. Таблиця place_photos: читати — усім, додавати — лише від власного імені.
alter table public.place_photos enable row level security;

drop policy if exists "place photos are publicly readable" on public.place_photos;
create policy "place photos are publicly readable" on public.place_photos
  for select using (true);

drop policy if exists "users can add their own place photos" on public.place_photos;
create policy "users can add their own place photos" on public.place_photos
  for insert to authenticated with check (auth.uid() = user_id);

notify pgrst, 'reload schema';

-- Перевірка після запуску:
--   select policyname from pg_policies where tablename = 'place_photos';
--   select policyname from pg_policies where schemaname = 'storage' and policyname like '%place photos%';
