-- Примусове оновлення застосунку: public.app_config (один рядок).
-- Адитивна міграція: нічого наявного не змінює й не забирає. Ідемпотентно.
--
-- Застосунок (AppVersionGate.kt) на старті читає РІВНО ці три колонки publishable-ключем, без входу:
--   min_version_code    — збірки з меншим versionCode показують повноекранне «Потрібне оновлення»;
--   latest_version_name — підпис нової версії на цьому екрані;
--   apk_url             — куди веде кнопка (лише https; інакше — сторінка релізів на GitHub).
--
-- КОНТРАКТ (CLAUDE.md): таблицю, ці три колонки й публічне читання НІКОЛИ не видаляти й не перейменовувати —
-- від них залежить кожна встановлена збірка. Нові колонки додавати можна.
--
-- Підняти мінімальну версію (SQL Editor або API від імені адміна):
--   update public.app_config set min_version_code = 4, latest_version_name = '1.2.0' where id = 1;
-- Обережно: значення, більше за versionCode найновішої опублікованої збірки, заблокує всіх.

create table if not exists public.app_config (
  id smallint primary key default 1 check (id = 1),
  min_version_code integer not null default 1 check (min_version_code >= 1),
  latest_version_name text,
  apk_url text check (apk_url is null or apk_url ~ '^https://'),
  updated_at timestamptz not null default now(),
  updated_by uuid references auth.users(id) on delete set null
);

alter table public.app_config enable row level security;

-- Читання — лише трьох колонок, усім (і без входу). Запис — лише адміну; вставляти/видаляти рядок через API не можна.
revoke all on public.app_config from anon, authenticated;
grant select (min_version_code, latest_version_name, apk_url) on public.app_config to anon, authenticated;
grant update (min_version_code, latest_version_name, apk_url) on public.app_config to authenticated;

drop policy if exists "app config is public" on public.app_config;
create policy "app config is public" on public.app_config
  for select to anon, authenticated
  using (true);

drop policy if exists "admin updates app config" on public.app_config;
create policy "admin updates app config" on public.app_config
  for update to authenticated
  using (public.is_admin())
  with check (public.is_admin());

create or replace function public.app_config_touch()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  new.updated_at := now();
  new.updated_by := auth.uid();
  return new;
end;
$$;

revoke all on function public.app_config_touch() from public, anon, authenticated;

drop trigger if exists app_config_touch on public.app_config;
create trigger app_config_touch
  before update on public.app_config
  for each row execute function public.app_config_touch();

-- Стартове значення 1 нікого не блокує. Підняти до 3 (перша збірка з цією перевіркою) — коли нову збірку
-- встановлено на всіх пристроях; старші збірки цієї перевірки не мають, на них значення не впливає.
insert into public.app_config (id, min_version_code, latest_version_name, apk_url)
values (1, 1, '1.1.1', 'https://github.com/maksymius12/mapa-inkluzyvnosti/releases/latest')
on conflict (id) do nothing;
