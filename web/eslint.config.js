// Minimal flat config, no external deps required for build.
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
