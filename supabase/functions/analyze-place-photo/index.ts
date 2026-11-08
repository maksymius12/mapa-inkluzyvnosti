// supabase/functions/analyze-place-photo/index.ts
//
// Аналіз фото входу до місця через OpenRouter (OpenAI-сумісний chat/completions, модель openai/gpt-4o).
// Застосунок викликає лише цю функцію (supabase.functions.invoke("analyze-place-photo", { photoUrl }))
// і ніколи не бачить ключ OpenRouter.
//
// Налаштування (один раз):
//   1. Секрет із ключем — ТІЛЬКИ як секрет функції, ніколи не в коді чи git:
//        Dashboard → Edge Functions → Secrets → OPENROUTER_API_KEY
//      або CLI: supabase secrets set OPENROUTER_API_KEY=...
//   2. Деплой: supabase functions deploy analyze-place-photo
//      (або Dashboard → Edge Functions → Create → вставити цей код). «Enforce JWT Verification» лишити
//      увімкненим; функція додатково перевіряє, що викликав саме авторизований користувач, а не анонім.
//
// Відповідь: {"isPlacePhoto": true | false, "title": "...", "summary": "...", "suggestions": ["..."],
//              "status": "accessible" | "partial" | "barrier",
//              "criteria": {"ramp": {"verdict": "yes" | "no" | "not_visible", "reason": "..."}, ... (7 критеріїв)}}.
// Запит може містити category: "transit" — тоді критерії зупинки (9; pavilion, accessible_transport,
// tactile_route з фото не перевірити — завжди not_visible), критичні — platform_height і obstacle_free.
// Критерій, якого не видно на фото, модель має чесно позначити not_visible, а не вгадувати.
// status ніколи не буде кращим, ніж випливає з критеріїв «ні» (сервер знижує його сам).
// isPlacePhoto = false — на фото не реальне місце (мем, скриншот, картинка з інтернету, селфі тощо):
// застосунок таке фото не зберігає.
// SUPABASE_URL і SUPABASE_ANON_KEY Supabase підставляє автоматично.

const OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";
const MODEL = "openai/gpt-4o";
const APP_URL = "https://github.com/maksymius12/mapa-inkluzyvnosti";
// Значення HTTP-заголовків — лише ASCII (fetch відхиляє кирилицю), тому назва транслітом.
const APP_TITLE = "Mapa Inkluzyvnosti";

const MAX_IMAGE_BYTES = 8 * 1024 * 1024;
const STATUSES = ["accessible", "partial", "barrier"];
const VERDICTS = ["yes", "no", "not_visible"];

// Ті самі 7 критеріїв, що й у перевірці застосунку (таблиця checks). Перші три — вхід: якщо хоч один «ні»,
// місце недоступне без сторонньої допомоги.
const CRITERIA: Record<string, string> = {
  ramp: "пандус або вхід без сходинок (рівний чи пологий підхід до дверей)",
  door_width: "ширина дверей щонайменше 80 см (проїде крісло колісне)",
  threshold: "немає порогів і перепадів висоти на вході понад 2 см",
  elevator: "ліфт або підйомник, якщо є поверхи вище чи нижче входу",
  toilet: "доступний туалет (просторий, з поручнями)",
  tactile: "тактильні позначки: рифлена плитка, контрастні смуги на сходах, шрифт Брайля",
  staff_assistance: "кнопка виклику персоналу чи табличка про допомогу персоналу",
};
const ENTRANCE = ["ramp", "door_width", "threshold"];

// Зупинка громадського транспорту (category = "transit"): 9 критеріїв (public.criteria_definitions).
// Оцінюємо ЛИШЕ те, що видно на одному фото; NOT_ON_PHOTO — завжди not_visible (з фото не перевірити).
const STOP_CRITERIA: Record<string, string> = {
  shelter: "на зупинці облаштовано навіс",
  seating: "облаштовано місця для сидіння (лави)",
  pavilion: "павільйон не ближче 2 м від дороги, лава не менше 3 м",
  tactile_strip: "попереджувальна тактильна смуга біля краю посадкового майданчика й інформаційна смуга місця посадки",
  passenger_info: "засоби інформування: електронне табло, таблиця з розкладом, схема руху",
  accessible_transport: "доступний транспорт не рідше 1 рейсу на годину",
  tactile_route: "тактильні елементи від закладів соцінфраструктури до зупинки",
  platform_height: "висота посадкового майданчика 20–25 см від дороги, урівень із низькою підлогою транспорту",
  obstacle_free: "доступ до зупинки вільний від перешкод (огорожі, опори, стенди, стовпчики, напівсфери)",
};
const STOP_NOT_ON_PHOTO = ["pavilion", "accessible_transport", "tactile_route"];
const STOP_CRITICAL = ["platform_height", "obstacle_free"];

const STOP_PROMPT =
  "Ти перевіряєш доступність зупинки громадського транспорту для людей з інвалідністю за фото.\n" +
  "1) Спершу визнач, чи це справжня фотографія реальної зупинки чи вулиці, зроблена на камеру. Мем, скриншот, " +
  "малюнок, картинка з інтернету, селфі — isPlacePhoto = false, у title коротко поясни чому, критерії — not_visible.\n" +
  "2) Інакше isPlacePhoto = true. Оціни ЛИШЕ те, що видно на фото. Для кожного критерію дай verdict і коротку " +
  "причину (reason, до 15 слів, українською):\n" +
  Object.entries(STOP_CRITERIA).filter(([key]) => !STOP_NOT_ON_PHOTO.includes(key))
    .map(([key, text]) => `- ${key}: ${text}`).join("\n") + "\n" +
  `Критерії ${STOP_NOT_ON_PHOTO.join(", ")} з одного фото не перевірити — для них завжди not_visible.\n` +
  "verdict: yes — на фото чітко видно, що вимогу виконано; no — чітко видно, що не виконано; not_visible — не " +
  "видно або не можна визначити напевно. НЕ вгадуй: якщо сумніваєшся, став not_visible.\n" +
  "3) summary — 2–3 речення українською: що видно на фото з погляду доступності зупинки.\n" +
  "4) title — головна проблема до 8 слів (або «Бар'єрів на зупинці не видно»). suggestions — 2–4 конкретні " +
  "пропозиції, як покращити доступність.\n" +
  "5) status: barrier — platform_height або obstacle_free має verdict no; partial — є інші «ні» або зупинку видно " +
  "не повністю; accessible — лише якщо все видиме виконано.";

const PROMPT =
  "Ти перевіряєш доступність місця для людей з інвалідністю за фото.\n" +
  "1) Спершу визнач, чи це справжня фотографія реального місця (будівля, вхід, приміщення, вулиця), зроблена " +
  "на камеру. Мем, скриншот, малюнок, картинка з написами чи шаблон з інтернету, селфі або щось, що не стосується " +
  "місця, — isPlacePhoto = false, у title коротко поясни чому, критерії — not_visible.\n" +
  "2) Інакше isPlacePhoto = true. Для КОЖНОГО з 7 критеріїв дай verdict і коротку причину (reason, до 15 слів, " +
  "українською, що саме видно на фото):\n" +
  Object.entries(CRITERIA).map(([key, text]) => `- ${key}: ${text}`).join("\n") + "\n" +
  "verdict: yes — на фото чітко видно, що вимогу виконано; no — чітко видно, що не виконано (наприклад, сходи " +
  "без пандуса, високий поріг, вузькі двері); not_visible — цього на фото не видно або не можна визначити напевно. " +
  "НЕ вгадуй: якщо сумніваєшся, став not_visible. Туалет, ліфт, тактильні позначки й кнопку виклику на фото входу " +
  "зазвичай не видно — тоді not_visible.\n" +
  "3) summary — 2–3 речення українською: що видно на фото з погляду доступності (сходи, пандус, поручні, ширина " +
  "дверей, поріг, покриття, освітлення).\n" +
  "4) title — головна проблема до 8 слів (або «Бар'єрів на вході не видно»). suggestions — 2–4 конкретні " +
  "пропозиції, як покращити доступність.\n" +
  "5) status: barrier — хоч один із ramp, door_width, threshold має verdict no; partial — є інші перешкоди або " +
  "вхід видно не повністю; accessible — лише якщо вхід добре видно і бар'єрів немає.";

// Structured outputs (OpenAI json_schema, strict): модель гарантовано повертає саме цей JSON.
function responseFormat(criteria: Record<string, string>) {
  return {
  type: "json_schema",
  json_schema: {
    name: "accessibility_analysis",
    strict: true,
    schema: {
      type: "object",
      properties: {
        isPlacePhoto: { type: "boolean" },
        title: { type: "string" },
        summary: { type: "string" },
        suggestions: { type: "array", items: { type: "string" } },
        status: { type: "string", enum: STATUSES },
        criteria: {
          type: "object",
          properties: Object.fromEntries(Object.keys(criteria).map((key) => [key, {
            type: "object",
            properties: {
              verdict: { type: "string", enum: VERDICTS },
              reason: { type: "string" },
            },
            required: ["verdict", "reason"],
            additionalProperties: false,
          }])),
          required: Object.keys(criteria),
          additionalProperties: false,
        },
      },
      required: ["isPlacePhoto", "title", "summary", "suggestions", "status", "criteria"],
      additionalProperties: false,
    },
  },
  };
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function toBase64(bytes: Uint8Array): string {
  let binary = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}

Deno.serve(async (req) => {
  try {
    if (req.method !== "POST") return json({ error: "method not allowed" }, 405);

    const apiKey = Deno.env.get("OPENROUTER_API_KEY");
    if (!apiKey) return json({ error: "OPENROUTER_API_KEY is not configured" }, 500);
    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;

    // Лише авторизовані користувачі: анонімний ключ є в кожному APK, тож без цього кредити OpenRouter
    // міг би витрачати будь-хто.
    const authHeader = req.headers.get("Authorization") ?? "";
    const userRes = await fetch(`${supabaseUrl}/auth/v1/user`, {
      headers: { apikey: anonKey, Authorization: authHeader },
    });
    if (!userRes.ok) return json({ error: "sign in required" }, 401);

    const { photoUrl, category } = await req.json().catch(() => ({}));
    // Набір критеріїв — за категорією місця (обмежує й промпт, і схему відповіді).
    const isStop = category === "transit";
    const criteriaSet = isStop ? STOP_CRITERIA : CRITERIA;
    const critical = isStop ? STOP_CRITICAL : ENTRANCE;
    // Аналізуємо лише фото з власного публічного бакета — функція не має бути проксі для довільних URL.
    const allowedPrefix = `${supabaseUrl}/storage/v1/object/public/place-photos/`;
    if (typeof photoUrl !== "string" || !photoUrl.startsWith(allowedPrefix)) {
      return json({ error: "photoUrl must point to the place-photos bucket" }, 400);
    }

    // Фото завантажуємо самі й передаємо як data URL: перевіряємо тип і розмір, і моделі не треба
    // самій ходити по посилання.
    const imageRes = await fetch(photoUrl);
    if (!imageRes.ok) return json({ error: `photo fetch failed: ${imageRes.status}` }, 400);
    const mimeType = (imageRes.headers.get("Content-Type") ?? "image/jpeg").split(";")[0];
    if (!mimeType.startsWith("image/")) return json({ error: "not an image" }, 400);
    const image = new Uint8Array(await imageRes.arrayBuffer());
    if (image.length > MAX_IMAGE_BYTES) return json({ error: "photo is too large" }, 413);

    const aiRes = await fetch(OPENROUTER_URL, {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${apiKey}`,
        "Content-Type": "application/json",
        "HTTP-Referer": APP_URL,
        "X-Title": APP_TITLE,
      },
      body: JSON.stringify({
        model: MODEL,
        messages: [{
          role: "user",
          content: [
            { type: "text", text: isStop ? STOP_PROMPT : PROMPT },
            { type: "image_url", image_url: { url: `data:${mimeType};base64,${toBase64(image)}` } },
          ],
        }],
        response_format: responseFormat(criteriaSet),
        temperature: 0.1,
        max_tokens: 1200,
      }),
    });
    const data = await aiRes.json().catch(() => null);
    if (!aiRes.ok || !data || data.error) {
      // Тіло помилки OpenRouter не містить ключа; логуємо для діагностики в Dashboard → Edge Functions → Logs.
      console.error("openrouter error", aiRes.status, JSON.stringify(data?.error ?? data));
      return json({ error: "analysis failed" }, 502);
    }

    const text: string = data?.choices?.[0]?.message?.content ?? "";
    const parsed = JSON.parse(text);

    const suggestions = Array.isArray(parsed.suggestions)
      ? parsed.suggestions.filter((s: unknown) => typeof s === "string" && s.trim()).slice(0, 4)
      : [];
    if (typeof parsed.isPlacePhoto !== "boolean" || typeof parsed.title !== "string" || !STATUSES.includes(parsed.status)) {
      return json({ error: "unexpected model output" }, 502);
    }
    const criteria: Record<string, { verdict: string; reason: string }> = {};
    for (const key of Object.keys(criteriaSet)) {
      const c = parsed.criteria?.[key];
      // Те, що з фото не перевірити, — завжди «не видно», навіть якщо модель вигадала відповідь.
      const forcedHidden = isStop && STOP_NOT_ON_PHOTO.includes(key);
      const verdict = !forcedHidden && VERDICTS.includes(c?.verdict) ? c.verdict : "not_visible";
      criteria[key] = { verdict, reason: typeof c?.reason === "string" ? c.reason.trim().slice(0, 160) : "" };
    }
    // ШІ може лише знизити оцінку: статус не кращий, ніж випливає з критеріїв «ні».
    const severity = (s: string) => STATUSES.indexOf(s);
    let status = parsed.status as string;
    if (critical.some((k) => criteria[k].verdict === "no")) status = "barrier";
    else if (Object.values(criteria).some((c) => c.verdict === "no") && severity(status) < severity("partial")) status = "partial";
    return json({
      isPlacePhoto: parsed.isPlacePhoto,
      title: parsed.title.trim().slice(0, 120),
      summary: typeof parsed.summary === "string" ? parsed.summary.trim().slice(0, 600) : "",
      suggestions,
      status,
      criteria,
    });
  } catch (e) {
    console.error("analyze-place-photo", e);
    return json({ error: e instanceof Error ? e.message : String(e) }, 500);
  }
});
