import tseslint from "typescript-eslint";
import reactHooks from "eslint-plugin-react-hooks";

// Runtime hazard checks; TypeScript checking is a separate command.
export default tseslint.config(
  { ignores: ["dist/**", "node_modules/**"] },
  {
    files: ["src/**/*.{ts,tsx}"],
    languageOptions: { parser: tseslint.parser },
    plugins: { "react-hooks": reactHooks },
    rules: {
      "constructor-super": "error",
      "no-cond-assign": ["error", "always"],
      "no-constant-condition": ["error", { checkLoops: false }],
      "no-debugger": "error",
      "no-dupe-args": "error",
      "no-dupe-keys": "error",
      "no-duplicate-case": "error",
      "no-eval": "error",
      "no-unexpected-multiline": "error",
      "no-unreachable": "error",
      "no-unsafe-finally": "error",
      "valid-typeof": "error",
    },
  },
);
