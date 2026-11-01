-- 1) Звіти «Цього місця не існує»: волонтер/учень (і адмін) повідомляє, що опубліковане місце не існує
--    чи не відповідає опису. Адмін схвалює (місце приховується, is_hidden = true) або відхиляє.
-- 2) Місячна аналітика для адмін-панелі.
--
-- Чому приховування, а не видалення: is_hidden уже використовує модерація скарг (place_reports), RLS
-- "visible places are publicly readable" ховає такі місця від усіх, а перевірки, фото й відгуки
-- лишаються — помилкове схвалення адмін може скасувати (is_hidden = false), нічого не втративши.
--
-- Залежить від 20261027130000 (is_admin, is_reviewer), 20261027160000 (адмін-панель).
-- Фото звіту завантажується в бакет place-photos — права на завантаження з 20261031120000.
-- Ідемпотентно. Виконати одним запуском у SQL Editor.

-- ============================================================
-- 1. Таблиця звітів
-- ============================================================
create table if not exists public.place_existence_reports (
  id uuid primary key default gen_random_uuid(),
  place_id text not null references public.places(id) on delete cascade,
  reported_by uuid not null references auth.users(id) on delete cascade,
  comment text not null check (char_length(btrim(comment)) between 5 and 1000),
  photo_url text,
  status text not null default 'pending' check (status in ('pending', 'approved', 'rejected')),
  created_at timestamptz not null default now(),
  reviewed_by uuid references auth.users(id) on delete set null,
  reviewed_at timestamptz
);
create index if not exists place_existence_reports_status_idx
  on public.place_existence_reports (status, created_at desc);
-- Один відкритий звіт на місце від одного користувача.
create unique index if not exists place_existence_reports_one_pending
  on public.place_existence_reports (place_id, reported_by) where status = 'pending';

alter table public.place_existence_reports enable row level security;

-- Подати — лише волонтер/учень (і адмін, у нього повний доступ) від власного імені й лише «очікує».
drop policy if exists "reviewers create existence reports" on public.place_existence_reports;
create policy "reviewers create existence reports" on public.place_existence_reports
  for insert to authenticated
  with check (
    reported_by = auth.uid()
    and status = 'pending'
    and reviewed_by is null
    and public.is_reviewer()
  );

-- Автор бачить свої звіти, адмін — усі. Звичайний мешканець доступу не має.
drop policy if exists "authors and admin read existence reports" on public.place_existence_reports;
create policy "authors and admin read existence reports" on public.place_existence_reports
  for select to authenticated
  using (reported_by = auth.uid() or public.is_admin());

drop policy if exists "admin updates existence reports" on public.place_existence_reports;
create policy "admin updates existence reports" on public.place_existence_reports
  for update to authenticated using (public.is_admin()) with check (public.is_admin());

drop policy if exists "admin deletes existence reports" on public.place_existence_reports;
create policy "admin deletes existence reports" on public.place_existence_reports
  for delete to authenticated using (public.is_admin());

-- ============================================================
-- 2. RPC: список для адміна, схвалення, відхилення
-- ============================================================
create or replace function public.admin_list_existence_reports(p_status text default 'pending')
returns table (
  id uuid,
  place_id text,
  place_name text,
  place_address text,
  place_hidden boolean,
  reported_by uuid,
  reporter_name text,
  reporter_email text,
  comment text,
  photo_url text,
  status text,
  created_at timestamptz,
  reviewed_at timestamptz
)
language plpgsql stable security definer
set search_path = public, extensions
as $$
begin
  if not public.is_admin() then
    raise exception using errcode = 'MP403', message = 'Лише для адміністратора';
  end if;
  return query
    select r.id, r.place_id, p.name, p.address, p.is_hidden, r.reported_by,
           pr.name, u.email::text, r.comment, r.photo_url, r.status, r.created_at, r.reviewed_at
    from public.place_existence_reports r
    join public.places p on p.id = r.place_id
    left join public.profiles pr on pr.id = r.reported_by
    left join auth.users u on u.id = r.reported_by
    where p_status is null or r.status = p_status
    order by r.created_at desc
    limit 200;
end;
$$;
revoke all on function public.admin_list_existence_reports(text) from public, anon;
grant execute on function public.admin_list_existence_reports(text) to authenticated;

-- Схвалити: місце приховується; інші відкриті звіти на те саме місце закриваються тим самим рішенням.
create or replace function public.approve_existence_report(p_report_id uuid)
returns void
language plpgsql security definer
set search_path = public, extensions
as $$
declare
  v_place_id text;
begin
  if not public.is_admin() then
    raise exception using errcode = 'MP403', message = 'Лише для адміністратора';
  end if;
  select place_id into v_place_id
  from public.place_existence_reports
  where id = p_report_id and status = 'pending'
  for update;
  if v_place_id is null then
    raise exception using errcode = 'MP404', message = 'Звіт не знайдено або вже розглянуто';
  end if;

  update public.places set is_hidden = true where id = v_place_id;

  update public.place_existence_reports
  set status = 'approved', reviewed_by = auth.uid(), reviewed_at = now()
  where place_id = v_place_id and status = 'pending';
end;
$$;
revoke all on function public.approve_existence_report(uuid) from public, anon;
grant execute on function public.approve_existence_report(uuid) to authenticated;

create or replace function public.reject_existence_report(p_report_id uuid)
returns void
language plpgsql security definer
set search_path = public, extensions
as $$
begin
  if not public.is_admin() then
    raise exception using errcode = 'MP403', message = 'Лише для адміністратора';
  end if;
  update public.place_existence_reports
  set status = 'rejected', reviewed_by = auth.uid(), reviewed_at = now()
  where id = p_report_id and status = 'pending';
  if not found then
    raise exception using errcode = 'MP404', message = 'Звіт не знайдено або вже розглянуто';
  end if;
end;
$$;
revoke all on function public.reject_existence_report(uuid) from public, anon;
grant execute on function public.reject_existence_report(uuid) to authenticated;

-- ============================================================
-- 3. Аналітика за місяць (будь-який день місяця → увесь місяць)
-- ============================================================
create or replace function public.admin_monthly_stats(p_month date)
returns table (
  new_users int,
  requests_total int,
  requests_approved int,
  requests_rejected int,
  new_reviews int,
  existence_reports_total int,
  existence_reports_approved int
)
language plpgsql stable security definer
set search_path = public, extensions
as $$
declare
  v_from timestamptz := date_trunc('month', p_month::timestamp);
  v_to timestamptz := date_trunc('month', p_month::timestamp) + interval '1 month';
  v_reviews int := 0;
begin
  if not public.is_admin() then
    raise exception using errcode = 'MP403', message = 'Лише для адміністратора';
  end if;
  -- Відгуки з'являються в 20261030130000; без неї рахуємо 0, а не падаємо.
  if to_regclass('public.place_reviews') is not null then
    execute 'select count(*)::int from public.place_reviews where created_at >= $1 and created_at < $2'
      into v_reviews using v_from, v_to;
  end if;

  return query
    select
      (select count(*)::int from auth.users u where u.created_at >= v_from and u.created_at < v_to),
      (select count(*)::int from public.place_requests q where q.created_at >= v_from and q.created_at < v_to),
      (select count(*)::int from public.place_requests q
         where q.created_at >= v_from and q.created_at < v_to and q.status = 'approved'),
      (select count(*)::int from public.place_requests q
         where q.created_at >= v_from and q.created_at < v_to and q.status = 'rejected'),
      v_reviews,
      (select count(*)::int from public.place_existence_reports r where r.created_at >= v_from and r.created_at < v_to),
      (select count(*)::int from public.place_existence_reports r
         where r.created_at >= v_from and r.created_at < v_to and r.status = 'approved');
end;
$$;
revoke all on function public.admin_monthly_stats(date) from public, anon;
grant execute on function public.admin_monthly_stats(date) to authenticated;

notify pgrst, 'reload schema';

-- Перевірка після запуску:
--   select * from public.admin_monthly_stats(current_date);  -- від імені адміна в застосунку; з SQL Editor — MP403
--   select policyname, cmd from pg_policies where tablename = 'place_existence_reports';
