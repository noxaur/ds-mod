import { integer, primaryKey, sqliteTable, text, uniqueIndex } from "void/schema-d1";

export const requests = sqliteTable("contrib_requests", {
  id: text("id").primaryKey(),
  // The brief, requirements, timeout and wanted count, as the agent sent them.
  spec: text("spec", { mode: "json" }).notNull(),
  status: text("status").notNull(),
  createdAt: text("created_at").notNull(),
});

export const skips = sqliteTable(
  "contrib_skips",
  { requestId: text("request_id").notNull(), userId: text("user_id").notNull() },
  (table) => [primaryKey({ columns: [table.requestId, table.userId] })],
);

// The capture itself is a file in storage, under `captures/<id>`.
export const submissions = sqliteTable(
  "contrib_submissions",
  {
    id: text("id").primaryKey(),
    requestId: text("request_id").notNull(),
    userId: text("user_id").notNull(),
    modVersion: text("mod_version").notNull(),
    bytes: integer("bytes").notNull(),
    createdAt: text("created_at").notNull(),
  },
  // One run per player per request, checked by the database so two requests at once cannot both pass.
  (table) => [uniqueIndex("contrib_submissions_one_per_player").on(table.requestId, table.userId)],
);

export const unknowns = sqliteTable(
  "contrib_unknowns",
  {
    kind: text("kind").notNull(),
    signature: text("signature").notNull(),
    sample: text("sample").notNull(),
    count: integer("count").notNull(),
    modVersion: text("mod_version").notNull(),
    firstSeen: text("first_seen").notNull(),
  },
  (table) => [primaryKey({ columns: [table.kind, table.signature] })],
);
