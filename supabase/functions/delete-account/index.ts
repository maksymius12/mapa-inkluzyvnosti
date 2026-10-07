// supabase/functions/delete-account/index.ts
//
// Видаляє акаунт користувача, що викликав функцію (Auth Admin API, потребує
// service-role — клієнтський SDK не має прав видалити користувача сам).
//
// Налаштування через Dashboard:
//   1. Edge Functions -> Create a new function -> назва "delete-account" -> вставити код -> Deploy.
//   2. "Enforce JWT Verification" лишити УВІМКНЕНИМ (на відміну від notify-sos) —
//      функція має знати, хто саме її викликав, щоб видалити саме цей акаунт,
//      а не будь-який на вимогу.
//
// SUPABASE_URL і SUPABASE_SERVICE_ROLE_KEY Supabase підставляє автоматично.

Deno.serve(async (req) => {
  try {
    const authHeader = req.headers.get("Authorization");
    if (!authHeader) {
      return new Response(JSON.stringify({ error: "missing Authorization header" }), { status: 401 });
    }
    const jwt = authHeader.replace("Bearer ", "");

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

    const userRes = await fetch(`${supabaseUrl}/auth/v1/user`, {
      headers: {
        apikey: serviceRoleKey,
        Authorization: `Bearer ${jwt}`
      }
    });
    if (!userRes.ok) {
      return new Response(JSON.stringify({ error: "invalid session" }), { status: 401 });
    }
    const user = await userRes.json();

    const deleteRes = await fetch(`${supabaseUrl}/auth/v1/admin/users/${user.id}`, {
      method: "DELETE",
      headers: {
        apikey: serviceRoleKey,
        Authorization: `Bearer ${serviceRoleKey}`
      }
    });
    if (!deleteRes.ok) {
      return new Response(JSON.stringify({ error: await deleteRes.text() }), { status: 500 });
    }

    return new Response(JSON.stringify({ deleted: true }), { headers: { "Content-Type": "application/json" } });
  } catch (e) {
    return new Response(JSON.stringify({ error: e instanceof Error ? e.message : String(e) }), { status: 500 });
  }
});
