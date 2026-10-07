-- SOS-запити з push-сповіщеннями волонтерам + токени пристроїв для FCM.

create table sos_requests (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users(id),
  lat double precision not null,
  lng double precision not null,
  problem_type text not null, -- 'obstacle' | 'physical_help' | 'other'
  comment text,
  status text not null default 'open', -- 'open' | 'resolved'
  resolved_by uuid references auth.users(id),
  created_at timestamptz default now(),
  resolved_at timestamptz
);
alter table sos_requests enable row level security;

create policy "sos_select_all" on sos_requests for select using (true);
create policy "sos_insert_own" on sos_requests for insert to authenticated with check (auth.uid() = user_id);
create policy "sos_update_resolve" on sos_requests for update to authenticated using (true);

create table device_tokens (
  user_id uuid references auth.users(id) primary key,
  fcm_token text not null,
  updated_at timestamptz default now()
);
alter table device_tokens enable row level security;

create policy "tokens_upsert_own" on device_tokens for insert to authenticated with check (auth.uid() = user_id);
create policy "tokens_update_own" on device_tokens for update to authenticated using (auth.uid() = user_id);

-- Bucket для фото місць — ідемпотентно, на випадок якщо ще не створений з першого раунду.
insert into storage.buckets (id, name, public) values ('place-photos', 'place-photos', true)
on conflict (id) do nothing;

-- ============================================================
-- Database webhook на INSERT у sos_requests -> Edge Function notify-sos.
-- Реалізовано напряму через pg_net (те, що Dashboard "Database Webhooks"
-- робить під капотом), щоб не залежати від ручного налаштування webhook
-- через UI. Edge Function деплоїться з --no-verify-jwt, тож окремий
-- секрет для авторизації виклику тут не потрібен.
-- ============================================================
create extension if not exists pg_net;

create or replace function public.notify_sos_request()
returns trigger as $$
begin
  perform net.http_post(
    url := 'https://ghtpngxjdepfuvslwwmx.supabase.co/functions/v1/notify-sos',
    headers := jsonb_build_object('Content-Type', 'application/json'),
    body := jsonb_build_object('record', row_to_json(new))
  );
  return new;
end;
$$ language plpgsql security definer;

create trigger sos_requests_notify
  after insert on sos_requests
  for each row execute function public.notify_sos_request();
