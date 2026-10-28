-- Адмін-панель у застосунку: список користувачів, зміна ролі (user/admin) і мети (мешканець/волонтер/учень).
-- Ідемпотентно. Виконати в SQL Editor після 20261027130000…150000.
--
-- Пошта користувачів лежить в auth.users, яку клієнт не читає, тож список — через security definer RPC.
-- Кожна функція сама перевіряє public.is_admin(); виконувати можуть лише авторизовані.

create or replace function public.admin_list_users(p_search text default null)
returns table (
  id uuid,
  email text,
  name text,
  role text,
  purpose_role text,
  age_group text,
  created_at timestamptz
)
language plpgsql stable security definer
set search_path = public, extensions
as $$
begin
  if not public.is_admin() then
    raise exception using errcode = 'MP403', message = 'Лише для адміністратора';
  end if;
  return query
    select u.id, u.email::text, p.name, coalesce(p.role, 'user'), p.purpose_role, p.age_group, u.created_at
    from auth.users u
    left join public.profiles p on p.id = u.id
    where p_search is null
       or btrim(p_search) = ''
       or u.email ilike '%' || btrim(p_search) || '%'
       or p.name ilike '%' || btrim(p_search) || '%'
    order by u.created_at desc
    limit 200;
end;
$$;
revoke all on function public.admin_list_users(text) from public, anon;
grant execute on function public.admin_list_users(text) to authenticated;

create or replace function public.admin_set_user_role(p_user_id uuid, p_role text)
returns void
language plpgsql security definer
set search_path = public, extensions
as $$
begin
  if not public.is_admin() then
    raise exception using errcode = 'MP403', message = 'Лише для адміністратора';
  end if;
  if p_role not in ('user', 'admin') then
    raise exception using errcode = 'MP400', message = 'Невідома роль';
  end if;
  -- Не залишити застосунок без адміністратора.
  if p_role = 'user'
     and exists (select 1 from public.profiles where id = p_user_id and role = 'admin')
     and (select count(*) from public.profiles where role = 'admin') <= 1 then
    raise exception using errcode = 'MP400', message = 'Не можна зняти роль з останнього адміністратора';
  end if;
  update public.profiles set role = p_role where id = p_user_id;
  if not found then
    raise exception using errcode = 'MP400', message = 'Профіль користувача не знайдено';
  end if;
end;
$$;
revoke all on function public.admin_set_user_role(uuid, text) from public, anon;
grant execute on function public.admin_set_user_role(uuid, text) to authenticated;

create or replace function public.admin_set_user_purpose(p_user_id uuid, p_purpose text)
returns void
language plpgsql security definer
set search_path = public, extensions
as $$
begin
  if not public.is_admin() then
    raise exception using errcode = 'MP403', message = 'Лише для адміністратора';
  end if;
  if p_purpose not in ('resident', 'volunteer', 'student') then
    raise exception using errcode = 'MP400', message = 'Невідома мета користування';
  end if;
  update public.profiles set purpose_role = p_purpose, roles_defaulted = false where id = p_user_id;
  if not found then
    raise exception using errcode = 'MP400', message = 'Профіль користувача не знайдено';
  end if;
end;
$$;
revoke all on function public.admin_set_user_purpose(uuid, text) from public, anon;
grant execute on function public.admin_set_user_purpose(uuid, text) to authenticated;

notify pgrst, 'reload schema';
