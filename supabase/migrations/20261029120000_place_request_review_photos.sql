-- Фото-доказ рецензента при схваленні заявки мешканця (place_requests).
-- Волонтер/учень/адмін перед «Схвалити» фотографує місце як підтвердження, що воно існує й відповідає заявці.
-- Без фото схвалити не можна (перевіряє сервер), відхилити — можна.
--
-- Де що зберігається:
--   * файл — у приватному bucket'і review-photos, шлях '<id заявки>/<uuid>.jpg';
--   * place_request_reviews.photo_path — доказ, прив'язаний до заявки. Окрема таблиця, а не колонка
--     в place_requests: автор заявки читає свій рядок place_requests (RLS — по рядках, не по колонках),
--     а доступ до доказу має бути лише в рецензентів;
--   * places.verification_photo_path — той самий шлях на опублікованому місці, щоб доказ лишався
--     прив'язаним до місця назавжди. Сам файл приватний — за шляхом його не відкрити без прав рецензента.
-- place_requests.reviewed_by / reviewed_at вже є (20261027130000) — їх заповнює approve, як і раніше.
--
-- Залежить від 20261027130000_admin_roles_and_requests.sql (place_requests, is_reviewer).
-- Ідемпотентно. Виконати одним запуском у SQL Editor.

-- 1. Bucket review-photos — приватний.
insert into storage.buckets (id, name, public) values ('review-photos', 'review-photos', false)
on conflict (id) do update set public = false;

-- Завантажувати — лише рецензент і лише в теку заявки, що ще очікує розгляду.
drop policy if exists "reviewers upload review photos" on storage.objects;
create policy "reviewers upload review photos" on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'review-photos'
    and public.is_reviewer()
    and exists (
      select 1 from public.place_requests r
      where r.id::text = (storage.foldername(name))[1] and r.status = 'pending'
    )
  );

-- Переглядати — лише рецензенти (мешканці й гості bucket не бачать).
drop policy if exists "reviewers read review photos" on storage.objects;
create policy "reviewers read review photos" on storage.objects
  for select to authenticated
  using (bucket_id = 'review-photos' and public.is_reviewer());

-- 2. Доказ рецензента до заявки. Пише лише approve_place_request (немає insert/update-політик).
create table if not exists public.place_request_reviews (
  request_id uuid primary key references public.place_requests(id) on delete cascade,
  reviewer_id uuid references auth.users(id) on delete set null,
  photo_path text not null,
  created_at timestamptz not null default now()
);
alter table public.place_request_reviews enable row level security;

drop policy if exists "reviewers read request reviews" on public.place_request_reviews;
create policy "reviewers read request reviews" on public.place_request_reviews
  for select to authenticated using (public.is_reviewer());

-- 3. Доказ на опублікованому місці. Клієнт не може ні поставити, ні змінити — лише approve_place_request.
alter table public.places add column if not exists verification_photo_path text;

create or replace function public.places_protect_verification_photo()
returns trigger
language plpgsql
set search_path = public, extensions
as $$
begin
  if current_user in ('authenticated', 'anon') then
    if tg_op = 'INSERT' then
      new.verification_photo_path := null;
    elsif new.verification_photo_path is distinct from old.verification_photo_path then
      raise exception using errcode = 'MP403', message = 'Ці поля змінювати не можна';
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists places_protect_verification_photo on public.places;
create trigger places_protect_verification_photo
  before insert or update on public.places
  for each row execute function public.places_protect_verification_photo();

-- 4. approve_place_request тепер вимагає фото-доказ. Стару версію (лише p_request_id) видаляємо,
--    інакше нею можна було б схвалити без фото.
drop function if exists public.approve_place_request(uuid);

create or replace function public.approve_place_request(p_request_id uuid, p_photo_path text)
returns public.places
language plpgsql security definer
set search_path = public, extensions
as $$
declare
  v_req public.place_requests;
  v_place public.places;
  v_path text := btrim(coalesce(p_photo_path, ''));
begin
  if not public.is_reviewer() then
    raise exception using errcode = 'MP403', message = 'Лише волонтери, учні чи адміністратор можуть розглядати заявки';
  end if;
  if v_path = '' then
    raise exception using errcode = 'MP400', message = 'Додайте фото-доказ, щоб схвалити заявку';
  end if;
  select * into v_req from public.place_requests where id = p_request_id and status = 'pending' for update;
  if v_req.id is null then
    raise exception using errcode = 'MP404', message = 'Заявку не знайдено або вже розглянуто';
  end if;
  -- Файл має реально лежати в review-photos, у теці саме цієї заявки, і бути завантаженим цим рецензентом.
  if split_part(v_path, '/', 1) <> p_request_id::text
     or not exists (
       select 1 from storage.objects o
       where o.bucket_id = 'review-photos' and o.name = v_path and o.owner_id = auth.uid()::text
     ) then
    raise exception using errcode = 'MP400', message = 'Фото-доказ не знайдено. Додайте фото ще раз';
  end if;

  insert into public.places (id, name, address, category, location, source, status, created_by, is_hidden, verification_photo_path)
  values ('user-' || gen_random_uuid()::text, v_req.name, v_req.address, v_req.category, v_req.location,
          'user', 'unverified', v_req.requester_id, false, v_path)
  returning * into v_place;

  update public.place_requests
  set status = 'approved', place_id = v_place.id, reviewed_by = auth.uid(), reviewed_at = now()
  where id = p_request_id;

  insert into public.place_request_reviews (request_id, reviewer_id, photo_path)
  values (p_request_id, auth.uid(), v_path)
  on conflict (request_id) do update
    set reviewer_id = excluded.reviewer_id, photo_path = excluded.photo_path, created_at = now();

  return v_place;
end;
$$;
revoke all on function public.approve_place_request(uuid, text) from public, anon;
grant execute on function public.approve_place_request(uuid, text) to authenticated;

notify pgrst, 'reload schema';

-- Перевірка після запуску:
--   select id, public from storage.buckets where id = 'review-photos';            -- public = false
--   select pg_get_function_identity_arguments(oid) from pg_proc
--     where proname = 'approve_place_request';                                  -- лише "p_request_id uuid, p_photo_path text"
--   select r.request_id, r.photo_path, p.verification_photo_path
--     from place_request_reviews r join place_requests q on q.id = r.request_id
--     join places p on p.id = q.place_id;                                       -- після першого схвалення
