// @keep Plain ESLint rule with no imports, so ESLint here and Oxlint in backend/ load the same file.
const secretName =
  /(?:[a-z\d](?:Key|Token|Secret|Password|Credential)|^(?:token|secret|password|credential)|_(?:KEY|TOKEN|SECRET|PASSWORD))s?$/u;

const nameOf = (node) => {
  if (node.type === "Identifier") {
    return node.name;
  }
  if (node.type === "MemberExpression" && node.computed === false && node.property.type === "Identifier") {
    return node.property.name;
  }
  if (node.type === "MemberExpression" && node.property.type === "Literal" && typeof node.property.value === "string") {
    return node.property.value;
  }
  if (node.type === "CallExpression") {
    return nameOf(node.callee);
  }
  return null;
};

const isUnset = (node) =>
  (node.type === "Literal" && (node.value === null || node.value === "")) ||
  (node.type === "Identifier" && node.name === "undefined") ||
  (node.type === "UnaryExpression" && node.operator === "typeof");

const isSecret = (node) => secretName.test(nameOf(node) ?? "");

const secretCompare = {
  meta: {
    type: "problem",
    messages: {
      secretCompare:
        "== and === stop at the first letter that differs, so a secret can be guessed a letter at a time. Compare it with sameSecret from web/shared/secret.ts, which also refuses an unset secret.",
    },
    schema: [],
  },
  create(context) {
    return {
      BinaryExpression(node) {
        if (!["==", "===", "!=", "!=="].includes(node.operator)) {
          return;
        }
        const { left, right } = node;
        if ((isSecret(left) && !isUnset(right)) || (isSecret(right) && !isUnset(left))) {
          context.report({ node, messageId: "secretCompare" });
        }
      },
    };
  },
};

export default secretCompare;
