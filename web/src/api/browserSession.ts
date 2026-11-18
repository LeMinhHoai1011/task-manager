/** Opt-in rollout: bearer/mobile endpoints remain available until migration approval. */
export const SECURE_BROWSER_SESSION=import.meta.env.VITE_SECURE_BROWSER_SESSION==="true";
export const BROWSER_SESSION_MARKER="tm.browser_session";
export async function browserCsrf(base:string):Promise<string>{
  const response=await fetch(`${base}/auth/web/csrf`,{credentials:"same-origin",cache:"no-store"});
  if(!response.ok)throw new Error("Browser session initialization failed");
  const body=await response.json();
  if(typeof body?.data?.csrf_token!=="string")throw new Error("Missing CSRF proof");
  return body.data.csrf_token;
}
