-- Хто може додавати фото місць: учні, волонтери й адмін (public.is_reviewer()), незалежно від віку.
-- Раніше в робочій базі діяли політики «лише дорослі» (age_group = 'adult'):
--   place_photos_insert_adult_own (public.place_photos) і photos_storage_insert_adult_only (storage.objects),
-- через що учень-рецензент не міг додати фото, а мешканець-дорослий — міг.
-- Кожне фото після завантаження перевіряє ШІ (Edge Function analyze-place-photo): якщо на ньому не місце,
-- а мем/скриншот/стороннє зображення, застосунок фото не зберігає й видаляє файл.
--
-- Нові файли застосунок кладе в теку '<user_id>/<place_id>/...' — так їх дозволяє видаляти наявна
-- політика photos_storage_delete_own (тека = auth.uid()).
--
-- Залежить від 20261027130000_admin_roles_and_requests.sql (is_reviewer). Ідемпотентно.

drop policy if exists "place_photos_insert_adult_own" on public.place_photos;
drop policy if exists "reviewers add their own place photos" on public.place_photos;
create policy "reviewers add their own place photos" on public.place_photos
  for insert to authenticated
  with check (user_id = auth.uid() and public.is_reviewer());

drop policy if exists "photos_storage_insert_adult_only" on storage.objects;
drop policy if exists "reviewers upload place photos" on storage.objects;
create policy "reviewers upload place photos" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'place-photos' and public.is_reviewer());

notify pgrst, 'reload schema';

-- Перевірка після запуску:
--   select policyname, cmd from pg_policies
--   where policyname in ('reviewers add their own place photos', 'reviewers upload place photos');
