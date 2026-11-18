import vi from "./vi";
import en from "./en";

export type Locale = "vi" | "en";

const dicts: Record<Locale, Record<string, string>> = { vi, en };

export function getLocale(): Locale { return "vi"; }

export function setLocale(l: Locale) {
  try {
    localStorage.setItem("tm.locale", l);
  } catch {
    /* ignore */
  }
}

/** Render message_key phía client. Không render text server. */
export function t(messageKey: string, locale?: Locale): string {
  const l = locale ?? getLocale();
  return dicts[l][messageKey] ?? dicts.vi[messageKey] ?? messageKey;
}
