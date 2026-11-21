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

const isName = (node, name) => node.type === "Identifier" && node.name === name;

const isMember = (node, object, property) =>
  node.type === "MemberExpression" &&
  node.computed === false &&
  isName(node.property, property) &&
  (isName(node.object, object) ||
    (node.object.type === "MemberExpression" &&
      node.object.computed === false &&
      isName(node.object.object, "globalThis") &&
      isName(node.object.property, object)));

const timeThroughEffect = defineRule({
  meta: {
    messages: {
      time: "A test cannot set this time. Read it with yield* Clock.currentTimeMillis, or yield* nowIso from src/ids.ts.",
      id: "Make an id with yield* newId from src/ids.ts, so every id is made in one place.",
    },
  },
  create(context) {
    return {
      NewExpression(node) {
        if (isName(node.callee, "Date") && node.arguments.length === 0) {
          context.report({ node, messageId: "time" });
        }
      },
      MemberExpression(node) {
        if (isMember(node, "Date", "now")) {
          context.report({ node, messageId: "time" });
        } else if (isMember(node, "crypto", "randomUUID")) {
          context.report({ node, messageId: "id" });
        }
      },
    };
  },
});

export default definePlugin({
  meta: { name: "dsmod" },
  rules: { "no-try": noTry, "time-through-effect": timeThroughEffect },
});
