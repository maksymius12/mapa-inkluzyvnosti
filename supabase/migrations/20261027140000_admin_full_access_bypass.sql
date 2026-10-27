-- Адмін повинен мати full доступ до places: і UPDATE (форма редагування),
-- і видалення через delete_own_place RPC — обидва шляхи досі мали власну
-- перевірку власника незалежно від RLS-політик "admin updates/deletes all places".
-- Виконати одним запуском у Supabase SQL Editor (проєкт MapaInkluzivnosti). Ідемпотентно.

create or replace function public.places_before_update()
returns trigger
language plpgsql
set search_path = public, extensions
as $$
begin
  if current_user in ('authenticated', 'anon') then
    if new.id is distinct from old.id
       or new.source is distinct from old.source
       or new.created_by is distinct from old.created_by
       or new.created_at is distinct from old.created_at
       or new.is_hidden is distinct from old.is_hidden then
      raise exception using errcode = 'MP403', message = 'Ці поля змінювати не можна';
    end if;

    if old.source = 'user'
       and old.created_by is distinct from auth.uid()
       and not public.is_admin()
       and (new.name is distinct from old.name
            or new.address is distinct from old.address
            or new.category is distinct from old.category
            or new.location is distinct from old.location) then
      raise exception using errcode = 'MP403', message = 'Змінювати можна лише власні місця';
    end if;
  end if;

  if new.source = 'user' then
    new.name := public.normalize_place_text(new.name);
    new.address := public.normalize_place_text(new.address);
    new.verified_note := coalesce(new.verified_note, '');
    perform public.validate_user_place(new.name, new.address, new.category);
    if new.location is distinct from old.location and not public.is_inside_district(new.location) then
      raise exception using errcode = 'MP404', message = 'Місце за межами району';
    end if;
  end if;

  return new;
end;
$$;

create or replace function public.delete_own_place(p_place_id text)
returns void
language plpgsql
security definer
set search_path = public, extensions
as $$
begin
  if auth.uid() is null then
    raise exception using errcode = 'MP401', message = 'Увійдіть, щоб видаляти місця';
  end if;
  if not exists (
    select 1 from public.places
    where id = p_place_id
      and ((source = 'user' and created_by = auth.uid()) or public.is_admin())
  ) then
    raise exception using errcode = 'MP403', message = 'Видаляти можна лише власні місця';
  end if;

  delete from public.checks where place_id = p_place_id;
  delete from public.reviews where place_id = p_place_id;
  delete from public.favorites where place_id = p_place_id;
  delete from public.place_photos where place_id = p_place_id;
  delete from public.places where id = p_place_id;
end;
$$;

notify pgrst, 'reload schema';

-- Перевірка після запуску:
--   select proname from pg_proc where proname = 'places_before_update';  -- має існувати
--   select is_admin();  -- true, якщо залогінені як maksymius12@gmail.com (адмін)
