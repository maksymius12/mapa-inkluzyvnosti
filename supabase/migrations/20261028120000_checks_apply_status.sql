-- Збереження перевірки за 7 критеріями: усе на сервері після INSERT у checks.
-- Ідемпотентно. Виконати в SQL Editor.
--
-- Що з'ясовано (2026-10-24/25, запити до REST API бази):
--  * до виконання 20261025120000_places_nearby.sql у базі не було checks.accessible_parking і
--    places.has_accessible_parking — кожне збереження перевірки падало з HTTP 400 PGRST204;
--  * функції increment_profile_progress у базі немає (HTTP 404 PGRST202) — у учнів збереження
--    закінчувалося помилкою вже після запису перевірки.
--
-- Тепер клієнт лише вставляє рядок у checks (RLS: user_id = auth.uid()). Тригер (security definer)
-- перераховує статус місця тією самою логікою, що й ComputeAccessStatus у застосунку (з урахуванням
-- ШІ-оцінки останнього фото: ШІ може лише понизити статус), оновлює has_accessible_parking і нараховує
-- бали учням. Для цього користувачу НЕ потрібні права на зміну місця (назви, категорії тощо).

-- 1. Політики checks: читати всім, додавати — лише від свого імені. Створюємо, лише якщо відповідних немає.
alter table public.checks enable row level security;

do $$
begin
  if not exists (
    select 1 from pg_policies where schemaname = 'public' and tablename = 'checks' and cmd in ('SELECT', 'ALL')
  ) then
    create policy "checks are publicly readable" on public.checks for select using (true);
  end if;
  if not exists (
    select 1 from pg_policies where schemaname = 'public' and tablename = 'checks' and cmd in ('INSERT', 'ALL')
  ) then
    create policy "users can add their own checks" on public.checks
      for insert to authenticated with check (auth.uid() = user_id);
  end if;
  -- Редагування власних місць (назва/категорія/адреса). Якщо політики UPDATE для places немає зовсім,
  -- автор не міг би нічого змінити (PATCH мовчки оновлював би 0 рядків). Зміст чужих місць захищає
  -- тригер places_before_update.
  if not exists (
    select 1 from pg_policies where schemaname = 'public' and tablename = 'places' and cmd in ('UPDATE', 'ALL')
  ) then
    create policy "authors update their own places" on public.places
      for update to authenticated
      using (source = 'user' and created_by = auth.uid())
      with check (source = 'user' and created_by = auth.uid());
  end if;
end;
$$;

-- 2. Статус місця за перевіркою.
create or replace function public.access_status_severity(p_status text)
returns int
language sql immutable
as $$
  select case p_status when 'barrier' then 3 when 'partial' then 2 when 'accessible' then 1 else 0 end;
$$;

create or replace function public.checks_after_insert_apply()
returns trigger
language plpgsql security definer
set search_path = public
as $$
declare
  v_computed text;
  v_ai text;
  v_final text;
begin
  -- ComputeAccessStatus: пандус + ширина дверей + без порогів — критичні; усі 7 — «доступно».
  if not (coalesce(new.ramp, false) and coalesce(new.door_width, false) and coalesce(new.threshold, false)) then
    v_computed := 'barrier';
  elsif coalesce(new.elevator, false) and coalesce(new.toilet, false)
        and coalesce(new.tactile, false) and coalesce(new.staff_assistance, false) then
    v_computed := 'accessible';
  else
    v_computed := 'partial';
  end if;

  -- combineWithAiStatus: ШІ-оцінка останнього фото може лише понизити статус.
  select ph.ai_suggested_status into v_ai
  from public.place_photos ph
  where ph.place_id = new.place_id
  order by ph.created_at desc
  limit 1;

  v_final := case
    when v_ai is not null and public.access_status_severity(v_ai) > public.access_status_severity(v_computed) then v_ai
    else v_computed
  end;

  update public.places
  set status = v_final,
      has_accessible_parking = coalesce(new.accessible_parking, false)
  where id = new.place_id;

  -- Бали учням (раніше — RPC increment_profile_progress з клієнта).
  update public.profiles
  set points = coalesce(points, 0) + 10,
      checks_count = coalesce(checks_count, 0) + 1
  where id = new.user_id and age_group = 'student';

  return new;
end;
$$;

drop trigger if exists checks_after_insert_apply on public.checks;
create trigger checks_after_insert_apply
  after insert on public.checks
  for each row execute function public.checks_after_insert_apply();

-- 3. Сумісність зі старими збірками застосунку, які ще викликають RPC: бали тепер нараховує тригер,
-- тож функція нічого не робить (і нікому не дає змінювати чужі бали).
create or replace function public.increment_profile_progress(p_user_id uuid, p_points_delta int)
returns void
language plpgsql
as $$
begin
  return;
end;
$$;
grant execute on function public.increment_profile_progress(uuid, int) to authenticated;

notify pgrst, 'reload schema';
