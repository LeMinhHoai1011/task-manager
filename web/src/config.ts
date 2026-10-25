/**
 * Build-time feature switches. Defaults preserve the full F4 feature set; a
 * deployment without an Ollama instance can build with VITE_ENABLE_AI=false
 * so the UI never offers a feature that cannot work.
 */
export const ENABLE_AI =
  (import.meta.env.VITE_ENABLE_AI ?? "true").toLowerCase() !== "false";
