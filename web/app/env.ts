export const backendUrl = process.env["BACKEND_URL"] ?? "http://127.0.0.1:16385";

export const trustedKey = () => process.env["AUTH_TRUSTED_KEY"];

export const inProduction = () => process.env.NODE_ENV === "production";
