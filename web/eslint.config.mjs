import js from "@eslint/js";
import nextVitals from "eslint-config-next/core-web-vitals";
import { defineConfig, globalIgnores } from "eslint/config";
import tseslint from "typescript-eslint";

const uiSdk = "ds-mod builds every interactive control from ui-sdk, so focus, keyboard and ARIA behave the same everywhere.";

// Each element ui-sdk has a component for, mapped to its entry.
const uiSdkElements = {
  button: "@ui-sdk/react/button",
  input: "@ui-sdk/react/input, checkbox, radio, switch, slider or another input component",
  textarea: "@ui-sdk/react/textarea",
  select: "@ui-sdk/react/select",
  option: "@ui-sdk/react/select or listbox",
  dialog: "@ui-sdk/react/dialog",
  details: "@ui-sdk/react/collapsible or accordion",
  summary: "@ui-sdk/react/collapsible or accordion",
  label: "@ui-sdk/react/label or field",
  meter: "@ui-sdk/react/meter",
  progress: "@ui-sdk/react/progress",
};

// Roles ui-sdk has a component for. A plain element with one of them hand-rolls that component.
const uiSdkRoles = [
  "listbox", "option", "menu", "menubar", "menuitem", "dialog", "alertdialog", "tablist", "tab", "tabpanel",
  "separator", "switch", "slider", "radiogroup", "radio", "checkbox", "toolbar", "tree", "treeitem", "combobox", "tooltip",
].join("|");

export default defineConfig(
  globalIgnores([".next/**", "out/**", "next-env.d.ts"]),
  js.configs.recommended,
  ...nextVitals,
  tseslint.configs.strictTypeChecked,
  tseslint.configs.stylisticTypeChecked,
  {
    linterOptions: { reportUnusedDisableDirectives: "error", reportUnusedInlineConfigs: "error" },
    languageOptions: { parserOptions: { projectService: true, tsconfigRootDir: import.meta.dirname } },
    rules: {
      eqeqeq: "error",
      curly: "error",
      "no-console": "error",
      "no-param-reassign": "error",
      "prefer-const": "error",
      "@typescript-eslint/consistent-type-imports": "error",
      "@typescript-eslint/consistent-type-exports": "error",
      "@typescript-eslint/strict-boolean-expressions": "error",
      "@typescript-eslint/switch-exhaustiveness-check": "error",
      "@typescript-eslint/no-shadow": "error",
      "@typescript-eslint/explicit-module-boundary-types": "off",
      "no-restricted-syntax": [
        "error",
        { selector: "TryStatement", message: "Model failure with Effect (Effect.try, Effect.tryPromise) instead of try/catch." },
        { selector: "JSXAttribute[name.name='popover']", message: `Use @ui-sdk/react/popover. ${uiSdk}` },
        {
          selector: `JSXAttribute[name.name='role'][value.value=/^(${uiSdkRoles})$/]`,
          message: `ui-sdk ships a component for this role; find it in the ui-sdk skill's build-interface table. ${uiSdk}`,
        },
      ],
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: [
                "@radix-ui/*", "radix-ui", "@headlessui/*", "react-aria", "react-aria-components", "@react-aria/*",
                "@react-stately/*", "@base-ui-components/*", "@base-ui/*", "@ariakit/*", "ariakit", "downshift",
                "@floating-ui/*", "cmdk", "vaul", "sonner", "react-select", "@mui/*", "@chakra-ui/*", "@mantine/*",
                "antd", "@ark-ui/*", "@zag-js/*",
              ],
              message: `Use the matching @ui-sdk/react component. ${uiSdk}`,
            },
          ],
        },
      ],
      "react/forbid-elements": [
        "error",
        { forbid: Object.entries(uiSdkElements).map(([element, use]) => ({ element, message: `Use ${use}. ${uiSdk}` })) },
      ],
    },
  },
  { files: ["**/*.{js,mjs,cjs}"], extends: [tseslint.configs.disableTypeChecked] },
);
