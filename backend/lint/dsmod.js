import { definePlugin, defineRule } from "vite-plus/lint/plugins";

const noTry = defineRule({
  meta: { messages: { noTry: "Model failure with Effect (Effect.try, Effect.tryPromise) instead of try/catch." } },
  create(context) {
    return {
      TryStatement(node) {
        context.report({ node, messageId: "noTry" });
      },
    };
  },
});

export default definePlugin({ meta: { name: "dsmod" }, rules: { "no-try": noTry } });
