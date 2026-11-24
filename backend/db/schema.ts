import { index, integer, primaryKey, sqliteTable, text, uniqueIndex } from "void/schema-d1";

export const requests = sqliteTable("contrib_requests", {
  id: text("id").primaryKey(),
  spec: text("spec", { mode: "json" }).notNull(),
  status: text("status").notNull(),
  createdAt: text("created_at").notNull(),
});

export const skips = sqliteTable(
  "contrib_skips",
  { requestId: text("request_id").notNull(), userId: text("user_id").notNull() },
  (table) => [primaryKey({ columns: [table.requestId, table.userId] })],
);

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

export const itemSeen = sqliteTable(
  "item_seen",
  {
    entry: text("entry").notNull(),
    userId: text("user_id").notNull(),
    glint: integer("glint").notNull(),
    plain: integer("plain").notNull(),
    modVersion: text("mod_version").notNull(),
  },
  (table) => [primaryKey({ columns: [table.entry, table.userId] }), index("item_seen_by_user").on(table.userId)],
);

export const itemTextures = sqliteTable(
  "item_textures",
  {
    entry: text("entry").notNull(),
    userId: text("user_id").notNull(),
    skinId: text("skin_id").notNull(),
    texture: text("texture").notNull(),
  },
  (table) => [
    primaryKey({ columns: [table.entry, table.userId, table.skinId] }),
    index("item_textures_by_user").on(table.userId),
  ],
);

// @keep The agent's view of an example leaves the user out.
export const itemSamples = sqliteTable(
  "item_samples",
  {
    entry: text("entry").notNull(),
    userId: text("user_id").notNull(),
    sample: text("sample", { mode: "json" }).notNull(),
  },
  (table) => [primaryKey({ columns: [table.entry, table.userId] })],
);
