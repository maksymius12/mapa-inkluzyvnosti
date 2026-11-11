-- Спільний чат (застосунок 1.2.0): один чат для всіх, хто увійшов. Лише текст, до 500 символів.
--
-- Крок «розширити» (CLAUDE.md §1): лише нові таблиця, функції, тригери й права. Наявні таблиці,
-- колонки, права й політики не змінюються, тож старі збірки працюють як раніше.
-- Перед застосуванням — резервна копія (tools/backup/backup-db.ps1).
--
-- Хто що може (через API):
--   * читати — лише authenticated; user_id не видно нікому (як у checks, 20261105120000):
--     SELECT лише на id, text, created_at, deleted; автор і ім'я — через RPC get_chat_messages();
--     видалені повідомлення бачать лише автор і адмін (решта — ні, ні через таблицю, ні через RPC);
--   * писати — лише authenticated і лише від себе: INSERT дозволено тільки в колонку text,
--     user_id ставить база (default auth.uid()), created_at — теж база, тож обійти ліміт частоти не можна;
--   * «видалити» — UPDATE лише колонки deleted: автор — своє, адмін — будь-яке; відновити видалене не можна;
--   * DELETE через API немає (ні прав, ні політики). Рядки зникають лише разом з акаунтом (on delete cascade).
-- Текст: без пробілів на краях і 1–500 символів — CHECK на рівні бази; тригер спершу обрізає пробіли.
-- Частота: не більше 1 повідомлення на 2 секунди й 30 за хвилину на користувача — тригер, SQLSTATE MP429.
-- Блокування користувачів поки немає (рішення власника 11.11.2026): адмін видаляє повідомлення.
-- Ідемпотентно.

create table if not exists public.chat_messages (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  text text not null,
  created_at timestamptz not null default now(),
  deleted boolean not null default false,
  constraint chat_messages_text_check check (
    char_length(text) between 1 and 500
    and text = btrim(text, E' \t\r\n')
  )
);

comment on table public.chat_messages is
  'Спільний чат застосунку. user_id не віддається через API: читання — RPC get_chat_messages().';

create index if not exists chat_messages_created_at_idx on public.chat_messages (created_at desc);
create index if not exists chat_messages_user_created_idx on public.chat_messages (user_id, created_at desc);

alter table public.chat_messages enable row level security;

-- Supabase за замовчуванням дає anon/authenticated усі права на нові таблиці — забираємо й видаємо точково.
revoke all on table public.chat_messages from public, anon, authenticated;
grant select (id, text, created_at, deleted) on public.chat_messages to authenticated;
grant insert (text) on public.chat_messages to authenticated;
grant update (deleted) on public.chat_messages to authenticated;

drop policy if exists "chat_messages_select" on public.chat_messages;
create policy "chat_messages_select" on public.chat_messages
  for select to authenticated
  using (not deleted or user_id = auth.uid() or public.is_admin());

drop policy if exists "chat_messages_insert_own" on public.chat_messages;
create policy "chat_messages_insert_own" on public.chat_messages
  for insert to authenticated
  with check (user_id = auth.uid());

drop policy if exists "chat_messages_update_own_or_admin" on public.chat_messages;
create policy "chat_messages_update_own_or_admin" on public.chat_messages
  for update to authenticated
  using (user_id = auth.uid() or public.is_admin())
  with check (user_id = auth.uid() or public.is_admin());

-- Перед вставкою: обрізати пробіли й перевірити частоту. security definer — щоб рахувати власні
-- повідомлення за user_id, який клієнтам читати не можна.
create or replace function public.chat_messages_before_insert()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_last timestamptz;
  v_recent integer;
begin
  new.text := btrim(new.text, E' \t\r\n');

  if new.user_id is null then
    raise exception using errcode = 'MP401', message = 'Увійдіть, щоб писати в чат';
  end if;

  -- Паралельні запити одного користувача йдуть по черзі, інакше обидва пройшли б перевірку.
  perform pg_advisory_xact_lock(hashtextextended('chat_messages:' || new.user_id::text, 0));

  select max(m.created_at), count(*)
    into v_last, v_recent
    from public.chat_messages m
   where m.user_id = new.user_id
     and m.created_at > now() - interval '1 minute';

  if v_last is not null and v_last > now() - interval '2 seconds' then
    raise exception using errcode = 'MP429',
      message = 'Не частіше ніж одне повідомлення на 2 секунди',
      hint = 'chat_rate_2s';
  end if;

  if v_recent >= 30 then
    raise exception using errcode = 'MP429',
      message = 'Не більше 30 повідомлень за хвилину. Зачекайте трохи',
      hint = 'chat_rate_minute';
  end if;

  return new;
end;
$$;

-- Видалене не відновлюється через API (інакше автор міг би повернути те, що прибрав адмін).
create or replace function public.chat_messages_before_update()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if old.deleted and not new.deleted and current_user in ('anon', 'authenticated') then
    raise exception using errcode = 'MP403', message = 'Видалене повідомлення не можна відновити';
  end if;
  return new;
end;
$$;

revoke all on function public.chat_messages_before_insert() from public, anon, authenticated;
revoke all on function public.chat_messages_before_update() from public, anon, authenticated;

drop trigger if exists chat_messages_before_insert on public.chat_messages;
create trigger chat_messages_before_insert
  before insert on public.chat_messages
  for each row execute function public.chat_messages_before_insert();

drop trigger if exists chat_messages_before_update on public.chat_messages;
create trigger chat_messages_before_update
  before update on public.chat_messages
  for each row execute function public.chat_messages_before_update();

-- Останні повідомлення (старі зверху, нові внизу) з іменем автора з profiles.name.
-- Без user_id і email; is_mine — щоб застосунок показав «Ви» і кнопку «Видалити» на своїх.
create or replace function public.get_chat_messages(p_limit integer default 100)
returns table (id uuid, author_name text, text text, created_at timestamptz, is_mine boolean)
language sql
stable
security definer
set search_path = ''
as $$
  select m.id,
         coalesce(nullif(btrim(p.name), ''), 'Користувач') as author_name,
         m.text,
         m.created_at,
         m.user_id = auth.uid() as is_mine
    from (
      select cm.id, cm.user_id, cm.text, cm.created_at
        from public.chat_messages cm
       where not cm.deleted
         and auth.uid() is not null
       order by cm.created_at desc, cm.id desc
       limit least(greatest(coalesce(p_limit, 100), 1), 200)
    ) m
    left join public.profiles p on p.id = m.user_id
   order by m.created_at asc, m.id asc;
$$;

revoke all on function public.get_chat_messages(integer) from public, anon;
grant execute on function public.get_chat_messages(integer) to authenticated;
