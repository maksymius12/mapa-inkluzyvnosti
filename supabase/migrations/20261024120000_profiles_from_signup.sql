-- Профіль створює сервер одразу після реєстрації (тригер на auth.users), а не клієнт.
-- Причина: з увімкненим «Confirm email» signUp не повертає сесію, тож клієнт не може
-- писати в profiles від імені користувача (RLS), — звідси була помилка «Немає активної сесії».
-- Застосунок передає ім'я, мету й вікову категорію в signUp як user metadata (raw_user_meta_data).
-- Виконати в SQL Editor проєкту Supabase. Політики RLS не змінюються.

-- false — мету й вікову категорію користувач ще не обирав (перший вхід через Google):
-- застосунок один раз покаже крок вибору. Наявні профілі отримують true, їх не питаємо.
alter table public.profiles
  add column if not exists roles_confirmed boolean not null default true;

create or replace function public.handle_new_user_profile()
returns trigger
language plpgsql security definer
set search_path = public
as $$
declare
  v_meta jsonb := coalesce(new.raw_user_meta_data, '{}'::jsonb);
  v_role_raw text := v_meta ->> 'purpose_role';
  v_age_raw text := v_meta ->> 'age_group';
  v_role text;
  v_age text;
  v_name text;
begin
  -- Лише дозволені значення, інакше — значення за замовчуванням.
  v_role := case when v_role_raw in ('resident', 'volunteer') then v_role_raw else 'resident' end;
  v_age := case when v_age_raw in ('student', 'adult') then v_age_raw else 'adult' end;

  v_name := btrim(regexp_replace(
    coalesce(
      nullif(btrim(v_meta ->> 'name'), ''),
      nullif(btrim(v_meta ->> 'full_name'), ''),
      nullif(split_part(coalesce(new.email, ''), '@', 1), ''),
      'Користувач'
    ),
    '[[:cntrl:]]', '', 'g'));
  v_name := left(coalesce(nullif(v_name, ''), 'Користувач'), 80);

  insert into public.profiles (id, name, purpose_role, age_group, roles_confirmed)
  values (
    new.id,
    v_name,
    v_role,
    v_age,
    coalesce(v_role_raw in ('resident', 'volunteer') and v_age_raw in ('student', 'adult'), false)
  )
  on conflict (id) do nothing;

  return new;
exception when others then
  -- Профіль не має блокувати реєстрацію: якщо щось пішло не так, застосунок
  -- створить його сам після першого входу (з тих самих метаданих).
  raise warning 'handle_new_user_profile: % (%)', sqlerrm, sqlstate;
  return new;
end;
$$;

revoke all on function public.handle_new_user_profile() from public, anon, authenticated;

drop trigger if exists on_auth_user_created_profile on auth.users;
create trigger on_auth_user_created_profile
  after insert on auth.users
  for each row execute function public.handle_new_user_profile();

-- Акаунти, які зареєструвалися, але залишилися без профілю (через цю помилку):
-- створюємо профіль зі значеннями з метаданих або за замовчуванням, вхід не блокуємо.
insert into public.profiles (id, name, purpose_role, age_group, roles_confirmed)
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
  true
from auth.users u
where not exists (select 1 from public.profiles p where p.id = u.id)
on conflict (id) do nothing;
