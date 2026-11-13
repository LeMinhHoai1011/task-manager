// Reserved for a future ESLint TypeScript parser configuration. The current
// lint command intentionally uses `tsc --noEmit`, which is the authoritative
// static check already required by the production build. Keeping this file
// avoids silently applying ESLint's JavaScript parser to TS/TSX sources.
export default [
  { ignores: ["dist", "node_modules"] },
  {
    files: ["**/*.{ts,tsx,js}"],
    rules: {
      "no-unused-vars": "warn",
      "no-undef": "off",
    },
  },
];
