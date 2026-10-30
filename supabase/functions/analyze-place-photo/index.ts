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
// Відповідь: {"title": "...", "suggestions": ["..."], "status": "accessible" | "partial" | "barrier"}.
// SUPABASE_URL і SUPABASE_ANON_KEY Supabase підставляє автоматично.

const OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";
const MODEL = "openai/gpt-4o";
const APP_URL = "https://github.com/maksymius12/mapa-inkluzyvnosti";
// Значення HTTP-заголовків — лише ASCII (fetch відхиляє кирилицю), тому назва транслітом.
const APP_TITLE = "Mapa Inkluzyvnosti";

const MAX_IMAGE_BYTES = 8 * 1024 * 1024;
const STATUSES = ["accessible", "partial", "barrier"];

const PROMPT =
  "Проаналізуй фото входу до будівлі на предмет доступності для людей з інвалідністю " +
  "(сходи, пандус, поріг, ширина дверей, поручні, покриття). Дай короткий заголовок головної проблеми " +
  "(до 8 слів), 2–4 конкретні пропозиції українською і статус: accessible — бар'єрів не видно, " +
  "partial — є перешкоди, але вхід можливий, barrier — без сторонньої допомоги не потрапити. " +
  "Якщо на фото не видно входу чи будівлі, так і напиши в заголовку і постав partial.";

// Structured outputs (OpenAI json_schema, strict): модель гарантовано повертає саме цей JSON.
const RESPONSE_FORMAT = {
  type: "json_schema",
  json_schema: {
    name: "accessibility_analysis",
    strict: true,
    schema: {
      type: "object",
      properties: {
        title: { type: "string" },
        suggestions: { type: "array", items: { type: "string" } },
        status: { type: "string", enum: STATUSES },
      },
      required: ["title", "suggestions", "status"],
      additionalProperties: false,
    },
  },
};

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

    const { photoUrl } = await req.json().catch(() => ({}));
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
            { type: "text", text: PROMPT },
            { type: "image_url", image_url: { url: `data:${mimeType};base64,${toBase64(image)}` } },
          ],
        }],
        response_format: RESPONSE_FORMAT,
        temperature: 0.2,
        max_tokens: 500,
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
    if (typeof parsed.title !== "string" || !STATUSES.includes(parsed.status)) {
      return json({ error: "unexpected model output" }, 502);
    }
    return json({ title: parsed.title.trim().slice(0, 120), suggestions, status: parsed.status });
  } catch (e) {
    console.error("analyze-place-photo", e);
    return json({ error: e instanceof Error ? e.message : String(e) }, 500);
  }
});
