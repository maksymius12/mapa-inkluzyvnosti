-- Окремий набір критеріїв для категорії «Зупинки громадського транспорту» (places.category = 'transit').
-- Залежить від 20261105120000 (права на колонки checks). Ідемпотентно.
--
-- * public.criteria_definitions — конфіг критеріїв за категорією (одне місце; нова категорія = нові рядки).
--   Поки що тут лише 'transit' (9 критеріїв методики «Мапи безбар'єрності», ЛУН 7200880–7200888).
--   Решта категорій і далі перевіряються за 7 колонками checks — для них нічого не змінюється.
-- * checks.criteria jsonb — відповіді перевірки зупинки {"key": true|false}; для інших категорій null.
-- * checks_before_insert_validate — сервер приймає перевірку зупинки лише з усіма 9 відповідями
--   (і без 7 колонок входу); для інших категорій criteria заборонено.
-- * checks_after_insert_apply — для зупинки статус за конфігом: будь-який критичний «Ні» — barrier;
--   усі «Так» — accessible; інакше — partial. ШІ-статус фото, як і раніше, може лише знизити.
--
-- Наявні дані: жоден рядок places / checks не оновлюється. Статуси зупинок (зокрема імпортованих із
-- відкритих даних) лишаються як є до першої нової перевірки зупинки.

create table if not exists public.criteria_definitions (
  category text not null,
  key text not null,
  position smallint not null,
  title text not null,
  details text not null,
  critical boolean not null,
  -- Чи можна оцінити з одного фото (для ШІ-аналізу); інакше ШІ відповідає «не видно».
  photo_checkable boolean not null default true,
  primary key (category, key)
);

alter table public.criteria_definitions enable row level security;
drop policy if exists "criteria definitions are public" on public.criteria_definitions;
create policy "criteria definitions are public" on public.criteria_definitions
  for select to anon, authenticated using (true);
revoke all on public.criteria_definitions from anon, authenticated;
grant select on public.criteria_definitions to anon, authenticated;

insert into public.criteria_definitions (category, key, position, title, details, critical, photo_checkable) values
  ('transit', 'shelter', 1, 'Навіс', 'На зупинці облаштовано навіс', false, true),
  ('transit', 'seating', 2, 'Місця для сидіння', 'Облаштовано місця для сидіння', false, true),
  ('transit', 'pavilion', 3, 'Розташування павільйону',
   'Лави з навісом (павільйони) стоять не ближче 2 м від проїзної частини, довжина лави не менше 3 м', false, false),
  ('transit', 'tactile_strip', 4, 'Тактильні смуги',
   'Є попереджувальна тактильна смуга біля краю посадкового майданчика та інформаційна тактильна смуга, що вказує місце посадки (передні двері)', false, true),
  ('transit', 'passenger_info', 5, 'Інформування пасажирів',
   'Є засоби інформування пасажирів (електронні табло, таблиці з розкладом, схема руху тощо)', false, true),
  ('transit', 'accessible_transport', 6, 'Доступний транспорт',
   'Через зупинку рухається доступний для осіб з інвалідністю транспорт, не рідше ніж 1 рейс на годину', false, false),
  ('transit', 'tactile_route', 7, 'Тактильний шлях до зупинки',
   'На пішохідній зоні є тактильні елементи доступності від закладів соцінфраструктури (освіта, охорона здоров''я, соцзахист, культура, ЦНАП, спорт тощо) до найближчої зупинки', false, false),
  ('transit', 'platform_height', 8, 'Висота посадкового майданчика',
   'Висота посадкового майданчика 20–25 см від рівня дороги, на рівні з низькою підлогою рухомого складу (або не нижче ніж 3 см від рівня підлоги)', true, true),
  ('transit', 'obstacle_free', 9, 'Вільний доступ',
   'Доступ до зупинки вільний від перешкод для пішоходів (споруди, огорожі, колони, опори, стенди, стовпчики, антипаркувальні напівсфери тощо)', true, true)
on conflict (category, key) do update
  set position = excluded.position, title = excluded.title, details = excluded.details,
      critical = excluded.critical, photo_checkable = excluded.photo_checkable;

alter table public.checks add column if not exists criteria jsonb;
alter table public.checks drop constraint if exists checks_criteria_object;
alter table public.checks add constraint checks_criteria_object
  check (criteria is null or jsonb_typeof(criteria) = 'object');

grant select (criteria) on public.checks to anon, authenticated;
grant insert (criteria) on public.checks to authenticated;

-- Повнота відповідей — на сервері, а не лише в UI.
create or replace function public.checks_before_insert_validate()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_category text;
  v_missing text;
  v_extra text;
begin
  select category into v_category from public.places where id = new.place_id;

  if exists (select 1 from public.criteria_definitions d where d.category = v_category) then
    if new.criteria is null then
      raise exception using errcode = 'MP422', message = 'Для цієї категорії потрібні відповіді на всі її критерії';
    end if;
    select string_agg(d.key, ', ' order by d.position) into v_missing
    from public.criteria_definitions d
    where d.category = v_category
      and jsonb_typeof(new.criteria -> d.key) is distinct from 'boolean';
    if v_missing is not null then
      raise exception using errcode = 'MP422', message = 'Не оцінено критерії: ' || v_missing;
    end if;
    select string_agg(k, ', ') into v_extra
    from jsonb_object_keys(new.criteria) k
    where not exists (select 1 from public.criteria_definitions d where d.category = v_category and d.key = k);
    if v_extra is not null then
      raise exception using errcode = 'MP422', message = 'Невідомі критерії: ' || v_extra;
    end if;
    -- 7 критеріїв входу до зупинки не застосовуються.
    new.ramp := null; new.door_width := null; new.threshold := null; new.elevator := null;
    new.toilet := null; new.tactile := null; new.staff_assistance := null; new.accessible_parking := null;
  elsif new.criteria is not null then
    raise exception using errcode = 'MP422', message = 'Для цієї категорії перевірка — за 7 критеріями входу';
  end if;
  return new;
end;
$$;

drop trigger if exists checks_before_insert_validate on public.checks;
create trigger checks_before_insert_validate
  before insert on public.checks
  for each row execute function public.checks_before_insert_validate();

-- Статус: для категорій із конфігом — за конфігом; для решти — як і раніше (7 колонок).
create or replace function public.checks_after_insert_apply()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_computed text;
  v_ai text;
  v_final text;
  v_by_definitions boolean := new.criteria is not null;
begin
  if v_by_definitions then
    select case
             when bool_or(d.critical and coalesce((new.criteria ->> d.key)::boolean, false) = false) then 'barrier'
             when bool_and(coalesce((new.criteria ->> d.key)::boolean, false)) then 'accessible'
             else 'partial'
           end
      into v_computed
    from public.criteria_definitions d
    join public.places p on p.category = d.category
    where p.id = new.place_id;
  -- ComputeAccessStatus: пандус + ширина дверей + без порогів — критичні; усі 7 — «доступно».
  elsif not (coalesce(new.ramp, false) and coalesce(new.door_width, false) and coalesce(new.threshold, false)) then
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

  if v_by_definitions then
    -- Паркування — атрибут закладу; перевірка зупинки його не змінює.
    update public.places set status = v_final where id = new.place_id;
  else
    update public.places
    set status = v_final,
        has_accessible_parking = coalesce(new.accessible_parking, false)
    where id = new.place_id;
  end if;

  -- Бали учням (раніше — RPC increment_profile_progress з клієнта).
  update public.profiles
  set points = coalesce(points, 0) + 10,
      checks_count = coalesce(checks_count, 0) + 1
  where id = new.user_id and age_group = 'student';

  return new;
end;
$$;
