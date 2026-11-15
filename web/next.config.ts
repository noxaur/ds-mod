import type { NextConfig } from "next";

const config: NextConfig = {
  reactStrictMode: true,
  // The dev server would print each action with its arguments, and the login's argument is the token.
  logging: { serverFunctions: false },
  // A page cannot set its own headers, so the login page gets them here.
  headers: () => [
    {
      source: "/login",
      headers: [
        { key: "Referrer-Policy", value: "no-referrer" },
        { key: "Cache-Control", value: "no-store" },
      ],
    },
  ],
};

export default config;
