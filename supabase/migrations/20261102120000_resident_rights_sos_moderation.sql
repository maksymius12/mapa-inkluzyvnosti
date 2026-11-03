-- Блок А + Б (MVP-правки 02.11):
--  1) Мешканець не змінює й не видаляє жодне місце (навіть своє) і не додає місце напряму — лише заявка
--     (submit_place_request) або пропозиція зміни назви (нова таблиця place_name_suggestions).
--     Волонтер / учень змінюють і видаляють власні місця, адмін — будь-які (як і раніше).
--  2) Зміна місця — лише через RPC update_place; прямий UPDATE таблиці places з клієнта заборонено
--     (revoke), тож мешканець отримує помилку навіть при прямому запиті до API, а не «0 рядків».
--  3) SOS: запит створюється одразу, тип проблеми й коментар доповнюються після відправки, локація має
--     джерело (gps / network / last_known / manual / none) і може бути відсутня. Читати SOS можуть лише
--     автор і рецензенти (раніше — будь-хто, навіть без входу); змінювати — автор (уточнення, скасування)
--     і рецензенти (лише «вирішено»).
--  4) moderation_counts() — лічильники того, що чекає розгляду (бейджі в профілі).
--
-- Залежить від 20261027130000 (is_admin, is_reviewer, place_requests),
-- 20261101120000 (place_existence_reports). Ідемпотентно.

-- ============================================================
-- 1. Місця: додавання / зміна / видалення
-- ============================================================

-- Мешканець додає місце лише через заявку (submit_place_request — security definer, RLS не потрібна).
drop policy if exists "users add their own places" on public.places;
drop policy if exists "reviewers add their own places" on public.places;
create policy "reviewers add their own places" on public.places
  for insert to authenticated
  with check (source = 'user' and created_by = auth.uid() and public.is_reviewer());

-- Клієнтський імпорт OSM у застосунку не використовується (мертвий код прибрано), а політика дозволяла
-- будь-кому, і мешканцю теж, додавати місця в обхід черги.
drop policy if exists "osm import adds osm places" on public.places;

-- Видалення — лише через delete_own_place (прибирає перевірки, фото, відгуки). Пряме DELETE автора
-- було другим шляхом, яким мешканець міг видалити своє місце.
drop policy if exists "authors delete their own places" on public.places;

-- Зміна — лише через update_place. Пряме UPDATE з клієнта закрито повністю: усі легітимні зміни
-- (статус після перевірки, рейтинг, приховування) роблять security definer функції й тригери.
drop policy if exists "authors update their own places" on public.places;
revoke update on public.places from anon, authenticated;

create or replace function public.can_manage_place(p_place public.places)
returns boolean
language sql
stable
security definer
set search_path = public, extensions
as $$
  select public.is_admin()
      or (p_place.source = 'user' and p_place.created_by = auth.uid() and public.is_reviewer());
$$;
revoke all on function public.can_manage_place(public.places) from public, anon;
grant execute on function public.can_manage_place(public.places) to authenticated;

create or replace function public.update_place(p_place_id text, p_name text, p_category text, p_address text)
returns void
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_place public.places;
begin
  if auth.uid() is null then
    raise exception using errcode = 'MP401', message = 'Увійдіть, щоб змінити місце';
  end if;
  select * into v_place from public.places where id = p_place_id for update;
  if v_place.id is null then
    raise exception using errcode = 'MP404', message = 'Місце не знайдено';
  end if;
  if not public.can_manage_place(v_place) then
    if not public.is_reviewer() then
      raise exception using errcode = 'MP403',
        message = 'Мешканці не можуть змінювати місця. Запропонуйте зміну назви — її розгляне волонтер';
    end if;
    raise exception using errcode = 'MP403', message = 'Змінювати можна лише власне місце';
  end if;
  -- Нормалізацію й перевірку полів робить тригер places_before_update.
  update public.places
  set name = p_name, category = p_category, address = coalesce(p_address, '')
  where id = p_place_id;
end;
$$;
revoke all on function public.update_place(text, text, text, text) from public, anon;
grant execute on function public.update_place(text, text, text, text) to authenticated;

create or replace function public.delete_own_place(p_place_id text)
returns void
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_place public.places;
begin
  if auth.uid() is null then
    raise exception using errcode = 'MP401', message = 'Увійдіть, щоб видалити місце';
  end if;
  select * into v_place from public.places where id = p_place_id;
  if v_place.id is null then
    raise exception using errcode = 'MP404', message = 'Місце не знайдено';
  end if;
  if not public.can_manage_place(v_place) then
    if not public.is_reviewer() then
      raise exception using errcode = 'MP403', message = 'Мешканці не можуть видаляти місця';
    end if;
    raise exception using errcode = 'MP403', message = 'Видаляти можна лише власне місце';
  end if;

  delete from public.checks where place_id = p_place_id;
  delete from public.reviews where place_id = p_place_id;
  delete from public.favorites where place_id = p_place_id;
  delete from public.place_photos where place_id = p_place_id;
  delete from public.places where id = p_place_id;
end;
$$;
revoke all on function public.delete_own_place(text) from public, anon;
grant execute on function public.delete_own_place(text) to authenticated;

-- ============================================================
-- 2. Пропозиції зміни назви (від мешканців; розглядає волонтер / учень / адмін)
-- ============================================================
create table if not exists public.place_name_suggestions (
  id uuid primary key default gen_random_uuid(),
  place_id text not null references public.places(id) on delete cascade,
  suggested_by uuid not null references auth.users(id) on delete cascade,
  current_name text not null,
  new_name text not null check (char_length(new_name) between 3 and 80),
  comment text check (comment is null or char_length(comment) <= 500),
  status text not null default 'pending' check (status in ('pending', 'approved', 'rejected')),
  created_at timestamptz not null default now(),
  reviewed_by uuid references auth.users(id) on delete set null,
  reviewed_at timestamptz
);
create index if not exists place_name_suggestions_status_idx
  on public.place_name_suggestions (status, created_at desc);
create unique index if not exists place_name_suggestions_one_pending
  on public.place_name_suggestions (place_id, suggested_by) where status = 'pending';

alter table public.place_name_suggestions enable row level security;

-- Читати: автор — свої, рецензенти — усі. Писати напряму не можна нікому: лише RPC нижче.
drop policy if exists "authors and reviewers read name suggestions" on public.place_name_suggestions;
create policy "authors and reviewers read name suggestions" on public.place_name_suggestions
  for select to authenticated
  using (suggested_by = auth.uid() or public.is_reviewer());

create or replace function public.suggest_place_name(p_place_id text, p_new_name text, p_comment text default null)
returns public.place_name_suggestions
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_place public.places;
  v_name text := public.normalize_place_text(p_new_name);
  v_comment text := nullif(btrim(coalesce(p_comment, '')), '');
  v_row public.place_name_suggestions;
begin
  if auth.uid() is null then
    raise exception using errcode = 'MP401', message = 'Увійдіть, щоб запропонувати зміну';
  end if;
  select * into v_place from public.places where id = p_place_id and is_hidden = false;
  if v_place.id is null then
    raise exception using errcode = 'MP404', message = 'Місце не знайдено';
  end if;
  if char_length(v_name) < 3 or char_length(v_name) > 80 or v_name ~ '[[:cntrl:]]' then
    raise exception using errcode = 'MP400', message = 'Назва має містити від 3 до 80 символів';
  end if;
  if lower(v_name) = lower(public.normalize_place_text(v_place.name)) then
    raise exception using errcode = 'MP400', message = 'Нова назва збігається з поточною';
  end if;
  if v_comment is not null and char_length(v_comment) > 500 then
    raise exception using errcode = 'MP400', message = 'Коментар задовгий (до 500 символів)';
  end if;

  insert into public.place_name_suggestions (place_id, suggested_by, current_name, new_name, comment)
  values (p_place_id, auth.uid(), v_place.name, v_name, v_comment)
  returning * into v_row;
  return v_row;
end;
$$;
revoke all on function public.suggest_place_name(text, text, text) from public, anon;
grant execute on function public.suggest_place_name(text, text, text) to authenticated;

create or replace function public.approve_name_suggestion(p_suggestion_id uuid)
returns void
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_row public.place_name_suggestions;
begin
  if not public.is_reviewer() then
    raise exception using errcode = 'MP403', message = 'Лише волонтер, учень чи адміністратор може розглядати пропозиції';
  end if;
  select * into v_row from public.place_name_suggestions
  where id = p_suggestion_id and status = 'pending' for update;
  if v_row.id is null then
    raise exception using errcode = 'MP404', message = 'Пропозицію не знайдено або вже розглянуто';
  end if;
  -- Інакше волонтер міг би перейменувати будь-яке чуже місце, схваливши власну пропозицію.
  if v_row.suggested_by = auth.uid() and not public.is_admin() then
    raise exception using errcode = 'MP403', message = 'Власну пропозицію має розглянути інший рецензент';
  end if;

  update public.places set name = v_row.new_name where id = v_row.place_id;

  update public.place_name_suggestions
  set status = 'approved', reviewed_by = auth.uid(), reviewed_at = now()
  where id = p_suggestion_id;
end;
$$;
revoke all on function public.approve_name_suggestion(uuid) from public, anon;
grant execute on function public.approve_name_suggestion(uuid) to authenticated;

create or replace function public.reject_name_suggestion(p_suggestion_id uuid)
returns void
language plpgsql
security definer
set search_path = public, extensions
as $$
begin
  if not public.is_reviewer() then
    raise exception using errcode = 'MP403', message = 'Лише волонтер, учень чи адміністратор може розглядати пропозиції';
  end if;
  update public.place_name_suggestions
  set status = 'rejected', reviewed_by = auth.uid(), reviewed_at = now()
  where id = p_suggestion_id and status = 'pending';
  if not found then
    raise exception using errcode = 'MP404', message = 'Пропозицію не знайдено або вже розглянуто';
  end if;
end;
$$;
revoke all on function public.reject_name_suggestion(uuid) from public, anon;
grant execute on function public.reject_name_suggestion(uuid) to authenticated;

-- ============================================================
-- 3. SOS
-- ============================================================
alter table public.sos_requests alter column lat drop not null;
alter table public.sos_requests alter column lng drop not null;
alter table public.sos_requests alter column problem_type drop not null;
alter table public.sos_requests add column if not exists location_source text not null default 'none';
alter table public.sos_requests add column if not exists location_accuracy_m real;
alter table public.sos_requests add column if not exists location_at timestamptz;
alter table public.sos_requests add column if not exists updated_at timestamptz not null default now();
alter table public.sos_requests add column if not exists cancelled_at timestamptz;

-- Наявні рядки мали координати з GPS / останньої відомої позиції — точніше джерело невідоме.
update public.sos_requests set location_source = 'last_known' where location_source = 'none' and lat is not null;

alter table public.sos_requests drop constraint if exists sos_requests_location_source_check;
alter table public.sos_requests add constraint sos_requests_location_source_check
  check (location_source in ('gps', 'network', 'last_known', 'manual', 'none'));
alter table public.sos_requests drop constraint if exists sos_requests_location_check;
alter table public.sos_requests add constraint sos_requests_location_check
  check ((lat is null) = (lng is null) and ((lat is null) = (location_source = 'none')));
alter table public.sos_requests drop constraint if exists sos_requests_problem_type_check;
alter table public.sos_requests add constraint sos_requests_problem_type_check
  check (problem_type is null or problem_type in ('cant_enter', 'stuck', 'escort', 'obstacle', 'physical_help', 'other'));
alter table public.sos_requests drop constraint if exists sos_requests_status_check;
alter table public.sos_requests add constraint sos_requests_status_check
  check (status in ('open', 'resolved', 'cancelled'));
alter table public.sos_requests drop constraint if exists sos_requests_comment_check;
alter table public.sos_requests add constraint sos_requests_comment_check
  check (comment is null or char_length(comment) <= 500);

-- Читати: автор і рецензенти (волонтер / учень / адмін). Раніше — будь-хто, навіть анонімно.
drop policy if exists "sos_select_all" on public.sos_requests;
drop policy if exists "sos authors and reviewers read" on public.sos_requests;
create policy "sos authors and reviewers read" on public.sos_requests
  for select to authenticated
  using (user_id = auth.uid() or public.is_reviewer());

drop policy if exists "sos_insert_own" on public.sos_requests;
create policy "sos_insert_own" on public.sos_requests
  for insert to authenticated
  with check (user_id = auth.uid());

-- Раніше будь-хто міг змінити будь-який SOS (using true). Тепер — автор або рецензент, а які саме поля,
-- перевіряє тригер sos_before_write.
drop policy if exists "sos_update_resolve" on public.sos_requests;
drop policy if exists "sos authors and reviewers update" on public.sos_requests;
create policy "sos authors and reviewers update" on public.sos_requests
  for update to authenticated
  using (user_id = auth.uid() or public.is_reviewer())
  with check (user_id = auth.uid() or public.is_reviewer());

create or replace function public.sos_before_write()
returns trigger
language plpgsql
set search_path = public, extensions
as $$
declare
  v_client boolean := current_user in ('authenticated', 'anon');
begin
  if not v_client then
    return new;
  end if;

  if tg_op = 'INSERT' then
    new.status := 'open';
    new.resolved_by := null;
    new.resolved_at := null;
    new.cancelled_at := null;
    new.created_at := now();
    new.updated_at := now();
    return new;
  end if;

  if new.id is distinct from old.id or new.user_id is distinct from old.user_id
     or new.created_at is distinct from old.created_at then
    raise exception using errcode = 'MP403', message = 'Ці поля змінювати не можна';
  end if;
  if old.status <> 'open' then
    raise exception using errcode = 'MP409', message = 'Запит уже закрито';
  end if;

  if old.user_id = auth.uid() then
    -- Автор: уточнює тип, коментар і локацію; може скасувати. «Вирішено» — лише якщо сам рецензент.
    if new.status = 'cancelled' then
      new.cancelled_at := now();
    elsif new.status = 'resolved' and not public.is_reviewer() then
      raise exception using errcode = 'MP403', message = 'Позначити вирішеним може волонтер';
    end if;
    if new.status = 'resolved' then
      new.resolved_by := auth.uid();
      new.resolved_at := now();
    elsif new.resolved_by is distinct from old.resolved_by or new.resolved_at is distinct from old.resolved_at then
      raise exception using errcode = 'MP403', message = 'Ці поля змінювати не можна';
    end if;
  else
    -- Рецензент чужого запиту: лише «вирішено», решта полів — без змін.
    if new.status <> 'resolved'
       or new.lat is distinct from old.lat or new.lng is distinct from old.lng
       or new.problem_type is distinct from old.problem_type or new.comment is distinct from old.comment
       or new.location_source is distinct from old.location_source
       or new.location_accuracy_m is distinct from old.location_accuracy_m
       or new.location_at is distinct from old.location_at then
      raise exception using errcode = 'MP403', message = 'Волонтер може лише позначити запит вирішеним';
    end if;
    new.resolved_by := auth.uid();
    new.resolved_at := now();
  end if;

  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists sos_before_write on public.sos_requests;
create trigger sos_before_write
  before insert or update on public.sos_requests
  for each row execute function public.sos_before_write();

-- ============================================================
-- 4. Лічильники модерації для бейджів
-- ============================================================
create or replace function public.moderation_counts()
returns table (place_requests integer, name_suggestions integer, existence_reports integer)
language plpgsql
stable
security definer
set search_path = public, extensions
as $$
begin
  if not public.is_reviewer() then
    return query select 0, 0, 0;
    return;
  end if;
  return query select
    (select count(*)::int from public.place_requests where status = 'pending'),
    (select count(*)::int from public.place_name_suggestions where status = 'pending'),
    case when public.is_admin()
      then (select count(*)::int from public.place_existence_reports where status = 'pending')
      else 0 end;
end;
$$;
revoke all on function public.moderation_counts() from public, anon;
grant execute on function public.moderation_counts() to authenticated;
