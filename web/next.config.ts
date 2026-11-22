import type { NextConfig } from "next";

const config: NextConfig = {
  reactStrictMode: true,
  // @keep The dev server would print each action with its arguments, and the login's argument is the token.
  logging: { serverFunctions: false },
  headers: () => [
    // @keep No other site may frame a page, so none can overlay the login's confirm button.
    {
      source: "/:path*",
      headers: [
        { key: "X-Frame-Options", value: "DENY" },
        { key: "X-Content-Type-Options", value: "nosniff" },
      ],
    },
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
