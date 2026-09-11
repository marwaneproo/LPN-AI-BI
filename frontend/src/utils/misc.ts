export function wait(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export function readNumberEnv(value: string | undefined, fallback: number) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}
