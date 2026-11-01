-- Виправлення до 20261029120000_place_request_review_photos.sql.
--
-- Політика "reviewers upload review photos" завжди відхиляла завантаження фото-доказу
-- ("new row violates row-level security policy"): у підзапиті
--   exists (select 1 from public.place_requests r where r.id::text = (storage.foldername(name))[1] ...)
-- некваліфіковане `name` Postgres бере з найближчої таблиці — place_requests.name (назва місця),
-- а не storage.objects.name (шлях файлу). Тепер шлях кваліфіковано: objects.name.
--
-- Ідемпотентно. Виконати одним запуском у SQL Editor.

drop policy if exists "reviewers upload review photos" on storage.objects;
create policy "reviewers upload review photos" on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'review-photos'
    and public.is_reviewer()
    and exists (
      select 1 from public.place_requests r
      where r.id::text = (storage.foldername(objects.name))[1] and r.status = 'pending'
    )
  );
