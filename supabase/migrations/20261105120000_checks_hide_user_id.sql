-- Перевірки (checks): автор перевірки (user_id) більше не видно клієнтам.
--
-- Було: політика checks_select_all (using true) для anon і authenticated + табличний SELECT — будь-хто,
-- навіть без входу, читав user_id кожної перевірки (хто, де й коли перевіряв).
-- Стало:
--   * SELECT лише на колонки без user_id (рядки й надалі публічні — картка місця показує останню перевірку);
--   * власні перевірки — RPC my_checks() (за auth.uid(), security definer);
--   * INSERT — лише з власним user_id (політика checks_insert_own уже є; додано default auth.uid());
--   * UPDATE/DELETE — лише власні (checks_update_own / checks_delete_own уже є), а змінити user_id
--     чи place_id в наявному рядку не можна (UPDATE лише на значення критеріїв і коментар);
--   * anon — жодних INSERT/UPDATE/DELETE (раніше табличні права були, рятувала лише відсутність політик).
--
-- Наявні рядки не змінюються: user_id лишається в базі (тригер балів, адмін-аналітика й service_role
-- його бачать), закрито лише читання через API.
-- Наслідок для старих збірок застосунку: вони читали checks через select=*, що тепер дає 42501 —
-- на мапі й у картці місця не буде останньої перевірки, доки застосунок не оновлено.
-- Ідемпотентно.

revoke select, insert, update, delete, references, trigger, truncate on public.checks from anon, authenticated;

grant select (id, place_id, ramp, door_width, threshold, elevator, toilet, tactile, staff_assistance,
              accessible_parking, comment, created_at)
  on public.checks to anon, authenticated;

grant insert (id, place_id, user_id, ramp, door_width, threshold, elevator, toilet, tactile, staff_assistance,
              accessible_parking, comment, created_at)
  on public.checks to authenticated;

grant update (ramp, door_width, threshold, elevator, toilet, tactile, staff_assistance, accessible_parking, comment)
  on public.checks to authenticated;

grant delete on public.checks to authenticated;

alter table public.checks alter column user_id set default auth.uid();

-- Політики ті самі за змістом; перестворюємо, щоб файл був самодостатнім.
drop policy if exists "checks_insert_own" on public.checks;
create policy "checks_insert_own" on public.checks
  for insert to authenticated
  with check (user_id = auth.uid());

drop policy if exists "checks_update_own" on public.checks;
create policy "checks_update_own" on public.checks
  for update to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

drop policy if exists "checks_delete_own" on public.checks;
create policy "checks_delete_own" on public.checks
  for delete to authenticated
  using (user_id = auth.uid());

-- Власні перевірки для «Твого внеску» в профілі.
create or replace function public.my_checks(p_limit integer default 200)
returns table (id uuid, place_id text, created_at timestamptz)
language sql
stable
security definer
set search_path = public
as $$
  select c.id, c.place_id, c.created_at
  from public.checks c
  where c.user_id = auth.uid()
  order by c.created_at desc
  limit least(greatest(coalesce(p_limit, 200), 1), 500);
$$;

revoke all on function public.my_checks(integer) from public, anon;
grant execute on function public.my_checks(integer) to authenticated;
