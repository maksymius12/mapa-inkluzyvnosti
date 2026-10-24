"""Потокове читання великих GeoJSON FeatureCollection без завантаження всього файлу в пам'ять."""
import json

_decoder = json.JSONDecoder()


def iter_features(path, chunk_size=1 << 20):
    """Повертає по одному об'єкту з масиву "features" (файл може мати сотні МБ)."""
    with open(path, encoding='utf-8') as f:
        buf = ''
        # Знайти початок масиву features.
        while True:
            chunk = f.read(chunk_size)
            if not chunk:
                return
            buf += chunk
            key = buf.find('"features"')
            if key != -1:
                bracket = buf.find('[', key)
                if bracket != -1:
                    buf = buf[bracket + 1:]
                    break
        eof = False
        while True:
            # Пропустити пробіли й коми між об'єктами.
            i = 0
            while i < len(buf) and buf[i] in ' \t\r\n,':
                i += 1
            buf = buf[i:]
            if buf.startswith(']'):
                return
            try:
                obj, end = _decoder.raw_decode(buf)
            except json.JSONDecodeError:
                if eof:
                    raise
                chunk = f.read(chunk_size)
                if not chunk:
                    eof = True
                buf += chunk
                continue
            yield obj
            buf = buf[end:]
            if len(buf) < 1024 and not eof:
                chunk = f.read(chunk_size)
                if not chunk:
                    eof = True
                buf += chunk
