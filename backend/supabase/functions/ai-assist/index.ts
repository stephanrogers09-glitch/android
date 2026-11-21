// Deno entry point for the ai-assist Edge Function. Not type-checked under Node; the logic
// lives in handler.ts and is covered by backend/scripts/test-functions.sh.
import { handleAiRequest, readEnv } from "./handler.ts";

declare const Deno: {
  env: { get(name: string): string | undefined };
  serve(handler: (req: Request) => Response | Promise<Response>): void;
};

Deno.serve((req) => {
  const resolved = readEnv((name) => Deno.env.get(name));
  if (!resolved.ok) {
    // A setup problem is reported, not a crash. The names are not secret; the values never appear.
    return new Response(
      JSON.stringify({ error: "server_not_configured", missing: resolved.missing }),
      { status: 503, headers: { "content-type": "application/json" } },
    );
  }
  return handleAiRequest(req, {
    fetch: (input, init) => fetch(input, init),
    env: resolved.env,
  });
});
