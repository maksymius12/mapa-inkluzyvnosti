"""Генерує SQL-імпорт об'єктів «Мапи безбар'єрності» (data.gov.ua) у межах Шевченківського району.

Запуск (з кореня репозиторію):
    python -I tools/opendata_import/build_import.py data/import supabase/imports

Вхід: GeoJSON-ресурси набору в data/import/ (не комітяться) + district_nominatim.json.
Вихід: supabase/imports/opendata_pilot_20.sql, opendata_part_NN.sql (до ~1 МБ кожен), import_report.md.
Ключі не потрібні: SQL виконується вручну в SQL Editor.
"""
import collections
import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from district import load_district, inside_district  # noqa: E402
from geojson_stream import iter_features  # noqa: E402
import mapping  # noqa: E402

DATASET_URL = 'https://data.gov.ua/dataset/38997a1f-2e86-4bd7-9054-cd9cd206d825'
ATTRIBUTION = "Дані: Мапа безбар'єрності (Мінрозвитку, ЛУН Місто), відкриті дані data.gov.ua, CC BY"
# Ресурси з точковими об'єктами. Не беремо: bombShelters (укриття), buildings (житло), pathways (лінії).
RESOURCES = ['establishments', 'others', 'objects', 'constructions', 'hotels', 'infrastructure', 'stops']
DUPLICATE_RADIUS_M = 30.0
PART_MAX_BYTES = 1_000_000

SHELTER_RX = re.compile(r'укритт|сховищ|\bПРУ\b|пункт незламності', re.I)
CRITICAL_RX = re.compile(r'водоканал|енерго|ДТЕК|нафтогаз|київгаз|укртелеком|підстанці|котельн|\bШЕУ\b', re.I)
# Об'єкти з іншого населеного пункту/району, що через помилку геокодування набору потрапили в межі району.
OTHER_PLACE_RX = re.compile(
    r'(?<!Київської )обласної ради|(?<!Київській )(?<!Київської )(?<!Київський )області|(?<!Київської )міської ради|'
    r"сільськ|селищн|Луцьк|"
    # Інший район Києва — лише «…ського району», щоб не чіпати назви ліній метро («Святошинсько-Броварська»).
    r"(Солом['’]янськ|Печерськ|Подільськ|Оболонськ|Святошинськ|Дарницьк|Деснянськ|Дніпровськ|Голосіївськ)\w*\s+район",
    re.I)
# «с. Назва», «м. Назва» (не Київ) — з урахуванням регістру, щоб не чіпати «ім. М. Костомарова».
OTHER_SETTLEMENT_RX = re.compile(r'(?<![\w.])с\.\s?[А-ЯІЇЄҐ]|(?<![\w.])м\.\s?(?!Києв|Київ)[А-ЯІЇЄҐ]')


def clean(text):
    return ' '.join((text or '').split())


def display_name(props, category):
    title = clean(props.get('title'))
    street = clean(props.get('addressThoroughfare'))
    if category == 'transit' and title and 'станці' not in title.lower() and 'пункт' not in title.lower():
        return f'Зупинка «{title}»'
    if not title and category == 'transit' and street:
        return f'Зупинка громадського транспорту, {street}'
    return title


def address_of(props):
    return clean(' '.join(x for x in [props.get('addressThoroughfare'), props.get('addressLocatorDesignator')] if x))


# --- схожість назв: дзеркало PlaceInputRules.isSimilarName і SQL place_names_similar -----------
_punct = re.compile(r'[^\w\s]', re.U)


def name_key(name):
    return ' '.join(_punct.sub(' ', (name or '').lower().replace('ё', 'е').replace('ʼ', "'")).split())


def levenshtein(a, b):
    prev = list(range(len(b) + 1))
    for i in range(1, len(a) + 1):
        cur = [i] + [0] * len(b)
        for j in range(1, len(b) + 1):
            cur[j] = min(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + (a[i - 1] != b[j - 1]))
        prev = cur
    return prev[-1]


def similar(a, b):
    x, y = name_key(a), name_key(b)
    if not x or not y:
        return False
    if x == y:
        return True
    if min(len(x), len(y)) >= 4 and (x in y or y in x):
        return True
    return levenshtein(x, y) <= max(1, max(len(x), len(y)) // 4)


def distance_m(lat1, lon1, lat2, lon2):
    r = 6371008.8
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


def sql_str(s):
    return "'" + (s or '').replace("'", "''") + "'"


def collect(data_dir):
    rings, bbox = load_district(os.path.join(data_dir, 'district_nominatim.json'))
    stats = collections.Counter()
    skipped = collections.defaultdict(list)
    candidates = []
    for res in RESOURCES:
        path = os.path.join(data_dir, res + '.geojson')
        for feat in iter_features(path):
            p = feat.get('properties') or {}
            stats['total'] += 1
            stats['total:' + res] += 1
            lat, lon = p.get('lat'), p.get('lon')
            if lat is None or lon is None:
                stats['no_coords'] += 1
                continue
            if not (bbox[0] <= lat <= bbox[2] and bbox[1] <= lon <= bbox[3]):
                continue
            stats['in_bbox'] += 1
            if not inside_district(lat, lon, rings):
                continue
            stats['in_district'] += 1
            title = clean(p.get('title'))
            category = mapping.map_category(p.get('kind'), title)
            name = display_name(p, category)
            item = f"{p.get('id')} · {title or '(без назви)'} · {address_of(p)}"
            if SHELTER_RX.search(title):
                skipped['укриття / пункт незламності'].append(item)
                continue
            if CRITICAL_RX.search(title):
                skipped['об\'єкт критичної інфраструктури'].append(item)
                continue
            if OTHER_PLACE_RX.search(title) or OTHER_SETTLEMENT_RX.search(title):
                skipped['у назві інший населений пункт чи район (помилка геокодування в наборі)'].append(item)
                continue
            if not name:
                skipped['немає назви'].append(item)
                continue
            criteria, parking = mapping.map_criteria(mapping.answers(p))
            candidates.append({
                'external_id': str(p.get('id')),
                'resource': res,
                'name': name[:200],
                'address': address_of(p)[:200],
                'category': category,
                'kind': p.get('kind'),
                'lat': float(lat),
                'lon': float(lon),
                'criteria': criteria,
                'parking': parking,
                'status': mapping.compute_status(criteria),
                'note': mapping.note_for(criteria, parking, p.get('updateDate')),
                'known': sum(v is not None for v in criteria.values()),
                'updated': p.get('updateDate') or '',
            })
    # Дублі всередині набору: той самий заклад кількома записами (≤30 м і схожа назва).
    # Лишаємо запис із найбільшою кількістю відомих критеріїв, далі — найновіший.
    candidates.sort(key=lambda c: (c['known'], c['updated']), reverse=True)
    kept = []
    for c in candidates:
        dup = next((k for k in kept
                    if distance_m(c['lat'], c['lon'], k['lat'], k['lon']) <= DUPLICATE_RADIUS_M and similar(c['name'], k['name'])), None)
        if dup:
            skipped['дубль усередині набору (≤30 м, схожа назва)'].append(
                f"{c['external_id']} · {c['name']} → лишили {dup['external_id']}")
            continue
        kept.append(c)
    kept.sort(key=lambda c: int(c['external_id']))
    return stats, skipped, kept


def row_sql(c):
    return '  ({}, {}, {}, {}, {!r}, {!r}, {}, {}, {})'.format(
        sql_str(c['external_id']), sql_str(c['name']), sql_str(c['address']), sql_str(c['category']),
        round(c['lon'], 7), round(c['lat'], 7), sql_str(c['status']), sql_str(c['note']),
        'true' if c['parking'] else 'false')


def statement(rows, title):
    body = ',\n'.join(rows)
    return f"""-- {title}
-- Джерело: «Моніторинг та оцінка ступеня безбар'єрності об'єктів фізичного оточення для осіб з інвалідністю»
-- (Мінрозвитку, ГО «ЛУН Місто»), {DATASET_URL}, ліцензія CC BY.
-- Ідемпотентно: повторний запуск нічого не дублює (ON CONFLICT (source, external_id) DO NOTHING).
-- Пропускає точки поза district_boundary і ті, біля яких (≤30 м) уже є місце зі схожою назвою.
-- Потрібні міграції 20261025120000_places_nearby.sql і 20261026120000_open_data_places.sql.

with v(external_id, name, address, category, lng, lat, status, note, parking) as (
  values
{body}
),
ins as (
  insert into public.places
    (id, name, address, category, location, status, source, verified_note,
     has_accessible_parking, external_id, attribution, created_by, is_hidden)
  select
    'opendata-' || v.external_id, v.name, v.address, v.category,
    ST_SetSRID(ST_MakePoint(v.lng, v.lat), 4326)::geography,
    v.status, 'open_data', v.note, v.parking, v.external_id,
    {sql_str(ATTRIBUTION)}, null, false
  from v
  where exists (
      select 1 from public.district_boundary b
      where ST_Contains(b.geom, ST_SetSRID(ST_MakePoint(v.lng, v.lat), 4326))
    )
    and not exists (
      select 1 from public.places p
      where p.source is distinct from 'open_data'
        and ST_DWithin(p.location::geography, ST_SetSRID(ST_MakePoint(v.lng, v.lat), 4326)::geography, {DUPLICATE_RADIUS_M})
        and public.place_names_similar(p.name, v.name)
    )
  on conflict (source, external_id) do nothing
  returning external_id
)
select
  (select count(*) from v) as rows_in_file,
  (select count(*) from ins) as inserted_now,
  (select count(*) from v) - (select count(*) from ins) as skipped_already_imported_duplicate_or_outside;
"""


def write_outputs(stats, skipped, kept, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    for f in os.listdir(out_dir):
        if f.startswith('opendata_') and f.endswith('.sql'):
            os.remove(os.path.join(out_dir, f))
    # Пробний файл: 20 точок, різні категорії й статуси.
    pilot, seen = [], collections.Counter()
    for c in sorted(kept, key=lambda c: (c['status'] == 'unverified', c['category'], c['external_id'])):
        if len(pilot) < 20 and seen[(c['category'], c['status'])] < 2:
            pilot.append(c)
            seen[(c['category'], c['status'])] += 1
    for c in kept:
        if len(pilot) >= 20:
            break
        if c not in pilot:
            pilot.append(c)
    pilot.sort(key=lambda c: int(c['external_id']))
    with open(os.path.join(out_dir, 'opendata_pilot_20.sql'), 'w', encoding='utf-8', newline='\n') as f:
        f.write(statement([row_sql(c) for c in pilot], 'ПРОБНИЙ імпорт: 20 точок'))
    # Основні частини до ~1 МБ.
    parts, cur, size = [], [], 0
    for c in kept:
        r = row_sql(c)
        if cur and size + len(r.encode('utf-8')) > PART_MAX_BYTES:
            parts.append(cur)
            cur, size = [], 0
        cur.append(r)
        size += len(r.encode('utf-8')) + 2
    if cur:
        parts.append(cur)
    for i, rows in enumerate(parts, 1):
        with open(os.path.join(out_dir, f'opendata_part_{i:02d}.sql'), 'w', encoding='utf-8', newline='\n') as f:
            f.write(statement(rows, f'Імпорт відкритих даних, частина {i} з {len(parts)} ({len(rows)} точок)'))
    # Звіт.
    status_c = collections.Counter(c['status'] for c in kept)
    cat_c = collections.Counter(c['category'] for c in kept)
    with open(os.path.join(out_dir, 'import_report.md'), 'w', encoding='utf-8', newline='\n') as f:
        f.write(f'# Звіт генерації імпорту\n\nНабір: {DATASET_URL}\n\n')
        f.write(f"- Об'єктів у 7 точкових ресурсах: {stats['total']}\n")
        for res in RESOURCES:
            f.write(f"  - {res}: {stats['total:' + res]}\n")
        f.write(f"- У прямокутнику району: {stats['in_bbox']}\n- У межах району (полігон): {stats['in_district']}\n")
        f.write(f"- До імпорту (після фільтрів і дедуплікації): {len(kept)} у {len(parts)} частин(і)\n\n")
        f.write('## Статуси\n' + ''.join(f'- {k}: {v}\n' for k, v in status_c.most_common()) + '\n')
        f.write('## Категорії\n' + ''.join(f'- {k}: {v}\n' for k, v in cat_c.most_common()) + '\n')
        f.write('## Пропущено\n')
        for reason, items in skipped.items():
            f.write(f'\n### {reason} — {len(items)}\n' + ''.join(f'- {i}\n' for i in items))
        f.write('\n## Пробні 20 точок\n\n| id | назва | категорія | статус | дані |\n|---|---|---|---|---|\n')
        for c in pilot:
            f.write(f"| {c['external_id']} | {c['name'][:60]} | {c['category']} | {c['status']} | {c['note'][:160] or '—'} |\n")
    return pilot, parts, status_c, cat_c


def main(data_dir, out_dir):
    sys.stdout.reconfigure(encoding='utf-8')
    stats, skipped, kept = collect(data_dir)
    pilot, parts, status_c, cat_c = write_outputs(stats, skipped, kept, out_dir)
    print('total', stats['total'], 'in_district', stats['in_district'], 'kept', len(kept), 'parts', len(parts))
    print('status', dict(status_c))
    print('category', dict(cat_c))
    print('skipped', {k: len(v) for k, v in skipped.items()})


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
