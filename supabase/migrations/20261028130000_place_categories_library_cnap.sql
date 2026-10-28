-- Нові категорії місць: library (бібліотеки) і cnap (ЦНАП — центри надання адміністративних послуг).
-- Раніше бібліотеки йшли в culture, а ЦНАП — в admin разом з управліннями й соцслужбами.
-- Ідемпотентно. Виконати в SQL Editor до (або після) імпорту відкритих даних — порядок не важливий:
-- перекласифікація нижче чіпає лише вже наявні рядки, а файл імпорту вже містить нові категорії.

-- 1. Валідатор категорії (дзеркало PlaceCategory у застосунку) — тепер з library і cnap.
create or replace function public.validate_user_place(p_name text, p_address text, p_category text)
returns void
language plpgsql immutable
as $$
begin
  if char_length(p_name) < 3 or char_length(p_name) > 80 then
    raise exception using errcode = 'MP400', message = 'Назва має містити від 3 до 80 символів';
  end if;
  if char_length(p_address) > 200 then
    raise exception using errcode = 'MP400', message = 'Адреса задовга (до 200 символів)';
  end if;
  if p_name ~ '[[:cntrl:]]' or p_address ~ '[[:cntrl:]]' then
    raise exception using errcode = 'MP400', message = 'Текст містить недопустимі символи';
  end if;
  if p_category is null or p_category not in (
    'education', 'health', 'culture', 'library', 'sport', 'cafe', 'services', 'cnap', 'admin', 'shop', 'transit'
  ) then
    raise exception using errcode = 'MP400', message = 'Оберіть категорію зі списку';
  end if;
end;
$$;

-- 2. Уже імпортовані відкриті дані — за назвою (ті самі правила, що в tools/opendata_import/mapping.py).
--    Користувацькі місця не чіпаємо: категорію там обрав автор.
update public.places
set category = 'library'
where source = 'open_data'
  and category <> 'library'
  and (name ilike '%бібліотек%' or name ~ '(ЦБС|ЦРБ)');

update public.places
set category = 'cnap'
where source = 'open_data'
  and category <> 'cnap'
  and (name ilike '%надання адміністративних послуг%' or name ~ 'ЦНАП');

notify pgrst, 'reload schema';

-- Перевірка після запуску:
--   select category, count(*) from public.places group by category order by 2 desc;
