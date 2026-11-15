import { Effect, Layer } from "effect";
import { HttpRouter, HttpServer } from "effect/http";
import { HttpApiBuilder } from "effect/http-api";

import { Api } from "./api.ts";

const Health = HttpApiBuilder.group(Api, "health", (handlers) => handlers.handle("healthz", () => Effect.void));

const web = HttpRouter.toWebHandler(
  HttpApiBuilder.layer(Api).pipe(Layer.provide([Health]), Layer.provide(HttpServer.layerServices)),
  { disableLogger: true },
);

/** The API as a function from request to response. */
export const app = (request: Request) => web.handler(request);
