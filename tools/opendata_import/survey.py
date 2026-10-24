"""Огляд набору: скільки об'єктів усього, у Києві, у межах Шевченківського району; типи (kind) і критерії."""
import collections
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from district import load_district, inside_district  # noqa: E402
from geojson_stream import iter_features  # noqa: E402

RESOURCES = ['establishments', 'others', 'objects', 'constructions', 'hotels', 'infrastructure', 'stops']


def main(data_dir, out_path):
    rings, bbox = load_district(os.path.join(data_dir, 'district_nominatim.json'))
    out = open(out_path, 'w', encoding='utf-8')
    grand = collections.Counter()
    kinds = collections.Counter()
    crit = collections.Counter()
    ids_seen = collections.Counter()
    for res in RESOURCES:
        c = collections.Counter()
        for feat in iter_features(os.path.join(data_dir, res + '.geojson')):
            p = feat.get('properties') or {}
            c['total'] += 1
            if p.get('addressPostName') == 'Київ' or 'Київ' == (p.get('addressAdminUnitL2') or ''):
                c['kyiv_address'] += 1
            lat, lon = p.get('lat'), p.get('lon')
            if lat is None or lon is None:
                c['no_coords'] += 1
                continue
            if not (bbox[0] <= lat <= bbox[2] and bbox[1] <= lon <= bbox[3]):
                continue
            c['in_bbox'] += 1
            if not inside_district(lat, lon, rings):
                continue
            c['in_district'] += 1
            ids_seen[p.get('id')] += 1
            kinds[(res, p.get('kind'))] += 1
            for cat in p.get('categories') or []:
                for cr in cat.get('criteria') or []:
                    crit[(cr.get('id'), cr.get('value'))] += 1
        out.write(f'{res}: {dict(c)}\n')
        grand.update(c)
    out.write(f'TOTAL: {dict(grand)}\n')
    out.write(f'duplicate ids across resources in district: {sum(1 for v in ids_seen.values() if v > 1)}\n\nKINDS in district:\n')
    for (res, kind), n in sorted(kinds.items(), key=lambda x: -x[1]):
        out.write(f'  {res} | {kind} | {n}\n')
    out.write('\nCRITERIA in district (id, value, count):\n')
    for (cid, val), n in sorted(crit.items(), key=lambda x: -x[1]):
        out.write(f'  {cid} | {val} | {n}\n')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
