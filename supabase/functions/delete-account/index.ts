// Deletes the account of the signed-in user, as the stores require.
// Their profile, nights and reports go with it (ON DELETE CASCADE); what they added
// to the directory stays, without author.
import { createClient } from "npm:@supabase/supabase-js@2";

const json = (body: unknown, status: number) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ code: "method_not_allowed" }, 405);

  const authorization = request.headers.get("Authorization");
  if (!authorization) return json({ code: "not_authenticated" }, 401);

  const url = Deno.env.get("SUPABASE_URL")!;
  const asUser = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: authorization } },
  });
  const { data, error } = await asUser.auth.getUser();
  if (error || !data.user) return json({ code: "not_authenticated" }, 401);

  // The service key never leaves the server.
  const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const deleted = await admin.auth.admin.deleteUser(data.user.id);
  if (deleted.error) return json({ code: "delete_failed" }, 500);

  return json({ deleted: true }, 200);
});
