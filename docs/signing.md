# Єдиний підпис збірок

Android ставить оновлення поверх лише тоді, коли новий APK підписано **тим самим** ключем.
Раніше CI підписував випадковим debug-ключем раннера (новим щоразу), а локальна збірка — debug-ключем
комп'ютера, тому доводилось видаляти застосунок. Тепер усі збірки підписуються одним release keystore.

Як це влаштовано:
- `app/build.gradle.kts` бере `MAPA_KEYSTORE_FILE`, `MAPA_KEYSTORE_PASSWORD`, `MAPA_KEY_ALIAS`, `MAPA_KEY_PASSWORD`
  зі змінних середовища (CI) або з `local.properties` (локально) і підписує ними debug і release.
- `.github/workflows/build-apk.yml` відновлює keystore із секрету `MAPA_KEYSTORE_BASE64` у тимчасову папку
  раннера, збирає, друкує відбиток сертифіката й видаляє файл. Без секретів збірка зупиняється з помилкою.

## Що зробити вручну (один раз)

Нічого з цього не комітити й не надсилати в чат.

### 1. Створити keystore (PowerShell)

Папка — поза репозиторієм і поза OneDrive-папкою проєкту, наприклад `C:\Users\maksy\keys`:

```powershell
New-Item -ItemType Directory -Force C:\Users\maksy\keys | Out-Null
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
  -keystore C:\Users\maksy\keys\mapa-release.jks -alias mapa `
  -keyalg RSA -keysize 4096 -validity 10000
```

keytool запитає пароль сховища та ім'я/організацію (можна коротко). Пароль ключа зробіть таким самим
(за замовчуванням для PKCS12 він і є таким самим). Паролі збережіть у менеджері паролів.

Зробіть 2 резервні копії `mapa-release.jks` (наприклад, флешка + менеджер паролів як вкладення).
Без цього файлу оновити встановлені збірки буде неможливо.

### 2. Додати в GitHub Secrets

Скопіювати keystore у base64 в буфер обміну:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\Users\maksy\keys\mapa-release.jks")) | Set-Clipboard
```

GitHub → репозиторій → Settings → Secrets and variables → Actions → New repository secret:

| Назва | Значення |
|---|---|
| `MAPA_KEYSTORE_BASE64` | вставити з буфера |
| `MAPA_KEYSTORE_PASSWORD` | пароль сховища |
| `MAPA_KEY_ALIAS` | `mapa` |
| `MAPA_KEY_PASSWORD` | пароль ключа |

Після цього очистіть буфер обміну (скопіюйте будь-що інше).
Також перевірте, що задано `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `GOOGLE_WEB_CLIENT_ID` — тепер CI зупиняється, якщо будь-якого бракує.

### 3. Локальна збірка

Дописати в `local.properties` (файл у `.gitignore`):

```
MAPA_KEYSTORE_FILE=C:/Users/maksy/keys/mapa-release.jks
MAPA_KEYSTORE_PASSWORD=...
MAPA_KEY_ALIAS=mapa
MAPA_KEY_PASSWORD=...
```

(Прямі скісні `/` у шляху — так `local.properties` не плутає `\` з екрануванням.)

### 4. Google Sign-In: додати новий відбиток

Вхід через Google перевіряє SHA-1 сертифіката. Отримати його:

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v `
  -keystore C:\Users\maksy\keys\mapa-release.jks -alias mapa
```

Google Cloud Console → APIs & Services → Credentials → OAuth client типу **Android** для
`com.academy.mapainkluzyvnosti` → додати новий SHA-1 (або створити ще один Android-клієнт із цим SHA-1).
Без цього вхід через Google у нових збірках не спрацює.

### 5. Перевірити

- Запустіть Actions → Build APK (Run workflow). У кроці **Verify signature** буде `SHA-256 digest`.
- Порівняйте з локальною збіркою (після `gradlew assembleDebug`):

```powershell
$bt = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" | Sort-Object Name | Select-Object -Last 1
& "$($bt.FullName)\apksigner.bat" verify --print-certs app\build\outputs\apk\debug\app-debug.apk
```

Відбитки мають збігатися.

### 6. Перехід (один раз)

Уже встановлені збірки підписані старими ключами, тож перший APK із новим ключем поверх не стане:
на кожному пристрої один раз видалити застосунок і встановити новий. Дані на сервері не зачіпаються,
губляться лише локальні налаштування й вхід. Далі всі оновлення ставляться поверх.
