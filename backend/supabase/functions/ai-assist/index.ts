// Deno entry point for the ai-assist Edge Function. Not type-checked under Node; the logic
// lives in handler.ts and is covered by backend/scripts/test-functions.sh.
import { handleAiRequest } from "./handler.ts";

declare const Deno: {
  env: { get(name: string): string | undefined };
  serve(handler: (req: Request) => Response | Promise<Response>): void;
};

function required(name: string): string {
  const value = Deno.env.get(name);
  if (!value) throw new Error(`missing environment variable ${name}`);
  return value;
}

Deno.serve((req) =>
  handleAiRequest(req, {
    fetch: (input, init) => fetch(input, init),
    env: {
      supabaseUrl: required("SUPABASE_URL"),
      supabaseAnonKey: required("SUPABASE_ANON_KEY"),
      supabaseServiceRoleKey: required("SUPABASE_SERVICE_ROLE_KEY"),
      geminiApiKey: required("GEMINI_API_KEY"),
      geminiModel: Deno.env.get("GEMINI_MODEL") ?? "gemini-2.5-flash",
    },
  }),
);
