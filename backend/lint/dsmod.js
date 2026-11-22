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

const noComputedConsole = defineRule({
  meta: {
    messages: {
      computedConsole:
        "no-console cannot see console[level]. Call console.error(..) where it is the Worker's log, or write through the logger that app.ts uses.",
    },
  },
  create(context) {
    return {
      MemberExpression(node) {
        if (node.object.type === "Identifier" && node.object.name === "console" && node.computed) {
          context.report({ node, messageId: "computedConsole" });
        }
      },
    };
  },
});

const noJsonParse = defineRule({
  meta: {
    messages: {
      jsonParse:
        "JSON.parse returns any. Decode with Schema.decodeUnknownEffect(Schema.fromJsonString(X)), so the shape is checked and a bad body is a failure.",
    },
  },
  create(context) {
    return {
      MemberExpression(node) {
        if (
          node.object.type === "Identifier" &&
          node.object.name === "JSON" &&
          !node.computed &&
          node.property.name === "parse"
        ) {
          context.report({ node, messageId: "jsonParse" });
        }
      },
    };
  },
});

export default definePlugin({
  meta: { name: "dsmod" },
  rules: { "no-try": noTry, "no-computed-console": noComputedConsole, "no-json-parse": noJsonParse },
});
