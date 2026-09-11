import { readNumberEnv } from "../utils/misc";

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "/api";
export const USE_MOCKS = import.meta.env.VITE_USE_MOCKS === "true";
export const API_TIMEOUT_SECONDS = readNumberEnv(import.meta.env.VITE_API_TIMEOUT_SECONDS, 260);

export const AUTH_UNAUTHORIZED_EVENT = "lpn:auth-unauthorized";

export async function apiFetch(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  if (!new Set(["GET", "HEAD", "OPTIONS"]).has(method)) {
    headers.set("X-LPN-Request", "web");
  }

  const response = await fetch(input, {
    ...init,
    headers,
    credentials: "include",
  });

  const url = String(input);
  const isCredentialAttempt = url.includes("/v1/auth/login") || url.includes("/v1/auth/signup");
  if (response.status === 401 && !isCredentialAttempt) {
    window.dispatchEvent(new Event(AUTH_UNAUTHORIZED_EVENT));
  }
  return response;
}
