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
  rules: {
    "no-try": noTry,
    "no-computed-console": noComputedConsole,
    "no-json-parse": noJsonParse,
    "time-through-effect": timeThroughEffect,
  },
});
