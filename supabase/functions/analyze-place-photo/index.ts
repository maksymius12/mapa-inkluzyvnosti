// supabase/functions/analyze-place-photo/index.ts
//
// Викликається з Kotlin-сторони через supabase.functions.invoke("analyze-place-photo", ...).
// Ключ OPENAI_API_KEY зберігається лише як секрет функції (supabase secrets set OPENAI_API_KEY=...)
// і ніколи не потрапляє в застосунок.

import { serve } from "https://deno.land/std/http/server.ts";

serve(async (req) => {
  const { photoUrl } = await req.json();
  const apiKey = Deno.env.get("OPENAI_API_KEY");

  const response = await fetch("https://api.openai.com/v1/responses", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${apiKey}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      model: "gpt-5.6-luna",
      input: [{
        role: "user",
        content: [
          { type: "input_text", text: "Проаналізуй фото входу до будівлі на предмет доступності для людей з інвалідністю. Дай короткий заголовок головної проблеми (до 8 слів), список із 2-4 конкретних пропозицій українською, і статус: accessible/partial/barrier. Відповідай лише JSON: {\"title\": \"\", \"suggestions\": [], \"status\": \"\"}" },
          { type: "input_image", image_url: photoUrl }
        ]
      }]
    })
  });

  const data = await response.json();
  return new Response(data.output_text, { headers: { "Content-Type": "application/json" } });
});
