-- Профілі для старих акаунтів + прапорці для застосунку. Ідемпотентно, можна запускати повторно.
-- Виконати в SQL Editor ПІСЛЯ 20261024120000_profiles_from_signup.sql (тригер створення профілю
-- для нових реєстрацій живе там; цей файл його не дублює).
--
-- Що з'ясовано (2026-10-24): у базі немає колонки profiles.roles_confirmed — тобто
-- 20261024120000_profiles_from_signup.sql не виконувалась, тригера на auth.users немає,
-- і нові акаунти лишалися без рядка в profiles.

-- 1. Колонки, які читає застосунок.
alter table public.profiles add column if not exists roles_confirmed boolean not null default true;
-- true — мету й вікову категорію підставлено за замовчуванням (рядок створено backfill-ом):
-- застосунок один раз запропонує перевірити їх у Налаштуваннях і скине прапорець після збереження.
alter table public.profiles add column if not exists roles_defaulted boolean not null default false;
-- Користувач уже бачив інструктаж «Як користуватися» (щоб не показувати після перевстановлення).
alter table public.profiles add column if not exists onboarding_done boolean not null default false;

-- 2. Читати й оновлювати свій рядок (id = auth.uid()). Створюємо лише якщо відповідної
-- політики ще немає — наявні політики не змінюємо. INSERT з клієнта не потрібен: рядок
-- створює тригер handle_new_user_profile або цей backfill.
alter table public.profiles enable row level security;

do $$
begin
  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'profiles' and cmd in ('SELECT', 'ALL')
  ) then
    create policy "users read their own profile" on public.profiles
      for select to authenticated using (auth.uid() = id);
  end if;

  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'profiles' and cmd in ('UPDATE', 'ALL')
  ) then
    create policy "users update their own profile" on public.profiles
      for update to authenticated using (auth.uid() = id) with check (auth.uid() = id);
  end if;
end;
$$;

-- 3. Backfill: рядок для кожного auth.users без профілю. Мета й вік — з метаданих реєстрації,
-- якщо вони там є (і допустимі), інакше за замовчуванням із позначкою roles_defaulted.
insert into public.profiles (id, name, purpose_role, age_group, roles_confirmed, roles_defaulted, onboarding_done)
select
  u.id,
  left(coalesce(
    nullif(btrim(u.raw_user_meta_data ->> 'name'), ''),
    nullif(btrim(u.raw_user_meta_data ->> 'full_name'), ''),
    nullif(split_part(coalesce(u.email, ''), '@', 1), ''),
    'Користувач'
  ), 80),
  case when u.raw_user_meta_data ->> 'purpose_role' in ('resident', 'volunteer')
       then u.raw_user_meta_data ->> 'purpose_role' else 'resident' end,
  case when u.raw_user_meta_data ->> 'age_group' in ('student', 'adult')
       then u.raw_user_meta_data ->> 'age_group' else 'adult' end,
  true,
  not (coalesce(u.raw_user_meta_data ->> 'purpose_role', '') in ('resident', 'volunteer')
       and coalesce(u.raw_user_meta_data ->> 'age_group', '') in ('student', 'adult')),
  false
from auth.users u
where not exists (select 1 from public.profiles p where p.id = u.id)
on conflict (id) do nothing;

notify pgrst, 'reload schema';

-- Перевірка після запуску (має повернути 0):
--   select count(*) from auth.users u where not exists (select 1 from public.profiles p where p.id = u.id);
