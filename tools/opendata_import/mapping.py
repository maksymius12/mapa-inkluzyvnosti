"""Зіставлення набору «Моніторинг та оцінка ступеня безбар'єрності…» (Мінрозвитку, data.gov.ua) з моделлю застосунку.

Критерій заповнюється лише з явного поля набору. Значення критерію: True / False / None (невідомо).
Статус рахується тією ж логікою, що й ComputeAccessStatus у застосунку, але в трьохзначній логіці:
якщо результат залежить від невідомих критеріїв — 'unverified'. 'accessible' — лише коли всі 7 = True.
"""
import re

YES, NO, NA = 'так', 'ні', 'не застосовується'

# --- Категорія закладу (kind набору -> PlaceCategory застосунку) ---------------------------
KIND_TO_CATEGORY = {
    'Освіта': 'education',
    'Культура, дозвілля': 'culture',
    'Парки, сквери': 'culture',
    'Медицина': 'health',
    'Аптеки': 'health',
    'Спорт': 'sport',
    'Кафе, ресторани': 'cafe',
    'Магазини': 'shop',
    'Банки, гроші': 'services',
    'Пошта': 'services',
    'Догляд, краса': 'services',
    'АЗС': 'services',
    'Готелі': 'services',
    'Соцпослуги': 'admin',
    'Транспорт': 'transit',
    'Вокзали': 'transit',
}
# «Інше» — за ключовими словами в назві; що не підійшло — «Сервіси».
OTHER_RULES = [
    (re.compile(r'управлінн|департамент|міністерств|адміністрац|\bРДА\b|КМДА', re.I), 'admin'),
    (re.compile(r'ринок|маркет|магазин', re.I), 'shop'),
    (re.compile(r'собор|церкв|храм|музей|театр|бібліотек', re.I), 'culture'),
]


def map_category(kind, title):
    if kind in KIND_TO_CATEGORY:
        return KIND_TO_CATEGORY[kind]
    for rx, category in OTHER_RULES:
        if rx.search(title or ''):
            return category
    return 'services'  # «Інше» та будь-які невідомі типи


# --- Критерії ----------------------------------------------------------------------------
# Пандус на вході = «на вході немає сходів або є сходи і пандус/підйомник».
RAMP_EQUIV = ['6600812', '8000947']                      # картка ПКМУ №537, вхідна група (так/ні)
RAMP_PRESENT = ['0100041', '0500078', '4700558', '4700701', '1800314']  # «Пандус при вході»
LEVEL_ENTRANCE = ['0100006', '0500089', '0100392']       # «Вхід безперешкодний, врівень з землею»
ENTRANCE_LIFT = ['0100044', '0500186']                    # «Є підйомник» (вхід)

# Ширина дверей: у наборі поріг 0,9 м, у нас 0,8 м -> «так» означає «так»; «ні» (<0,9 м) не означає <0,8 м.
DOOR_09 = ['6600818', '8000952', '0100010', '0500080', '4700528', '6200777', '1800315', '4300510']

# Немає порогів на вході. 6600823 («решітка для витирання ніг … і перепади висоти підлоги») НЕ беремо:
# «ні» там може означати лише решітку, тож це не явна відповідність нашому критерію.
THRESHOLD = ['6600819', '8000953', '0100011', '0500191']

# Ліфт (за потреби): «у разі розташування вище 1-го поверху обладнані ліфтом…»; «не застосовується» = потреби немає.
ELEVATOR_NEED = ['6800852', '8200983']
ELEVATOR_ALT = ['6800859', '8200990']                     # «у разі відсутності ліфта послуга на 1-му поверсі»
ELEVATOR_PRESENT = ['0200049', '0500081', '0500069']      # «Ліфт», «Є ліфт»

# Доступний туалет.
TOILET = ['6700835', '8100968', '0500198', '0200018', '0500207']

# Тактильні позначки (таблички, Брайль, мнемосхеми, тактильні смуги, кнопки ліфта).
TACTILE = ['6600811', '6700841', '6700837', '6600815', '6700828', '6800851', '6800857',
           '7700470', '0500231', '0200045', '0500195']

# Допомога персоналу — лише явна послуга супроводу. «Коректна комунікація» й тренінги НЕ є допомогою.
STAFF = ['8301000']

# Паркування для МГН (не входить у статус).
PARKING = ['6500798', '7900933', '1500215', '0500165', '0500025', '0500091', '5200330']

CRITERIA_LABELS = {
    'ramp': 'вхід без сходів або з пандусом/підйомником',
    'door_width': 'ширина дверей ≥ 0,9 м',
    'threshold': 'без порогів на вході',
    'elevator': 'ліфт (за потреби)',
    'toilet': 'доступний туалет',
    'tactile': 'тактильні позначки',
    'staff_assistance': 'допомога персоналу',
}


def answers(props):
    """{criterion_id: value} з усіх категорій об'єкта."""
    out = {}
    for cat in props.get('categories') or []:
        for cr in cat.get('criteria') or []:
            cid, val = cr.get('id'), (cr.get('value') or '').strip()
            if cid:
                out[cid] = val
    return out


def _vals(a, ids):
    return [a[i] for i in ids if i in a]


def map_criteria(a):
    c = {}
    # ramp
    if YES in _vals(a, RAMP_EQUIV + RAMP_PRESENT + LEVEL_ENTRANCE + ENTRANCE_LIFT):
        c['ramp'] = True
    elif NO in _vals(a, RAMP_EQUIV):
        c['ramp'] = False
    elif NO in _vals(a, RAMP_PRESENT) and NO in _vals(a, LEVEL_ENTRANCE):
        c['ramp'] = False
    else:
        c['ramp'] = None
    # door width: лише «так»
    c['door_width'] = True if YES in _vals(a, DOOR_09) else None
    # threshold
    tv = _vals(a, THRESHOLD)
    c['threshold'] = False if NO in tv else (True if YES in tv else None)
    # elevator
    need = _vals(a, ELEVATOR_NEED)
    alt = _vals(a, ELEVATOR_ALT)
    if YES in need or NA in need or YES in alt or YES in _vals(a, ELEVATOR_PRESENT):
        c['elevator'] = True
    elif NO in need:
        c['elevator'] = False
    else:
        c['elevator'] = None
    # toilet («не застосовується» = загальних туалетів немає -> невідомо, не «так»)
    tl = _vals(a, TOILET)
    c['toilet'] = True if YES in tl else (False if NO in tl else None)
    # tactile
    tc = _vals(a, TACTILE)
    c['tactile'] = True if YES in tc else (False if NO in tc else None)
    # staff
    st = _vals(a, STAFF)
    c['staff_assistance'] = True if YES in st else (False if NO in st else None)
    parking = YES in _vals(a, PARKING)
    return c, parking


def compute_status(c):
    """ComputeAccessStatus у трьохзначній логіці. Ніколи не 'accessible' з неповних даних."""
    critical = [c['ramp'], c['door_width'], c['threshold']]
    others = [c['elevator'], c['toilet'], c['tactile'], c['staff_assistance']]
    if False in critical:
        return 'barrier'
    if all(v is True for v in critical):
        if False in others:
            return 'partial'
        if all(v is True for v in others):
            return 'accessible'
    return 'unverified'


def note_for(c, parking, update_date):
    parts = [f"{CRITERIA_LABELS[k]} — {'так' if v else 'ні'}" for k, v in c.items() if v is not None]
    if parking:
        parts.append('паркування для осіб з інвалідністю — так')
    if not parts:
        return ''
    when = f' (станом на {update_date})' if update_date else ''
    return 'За відкритими даними' + when + ': ' + '; '.join(parts) + '.'
