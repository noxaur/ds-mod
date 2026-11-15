import { index, integer, primaryKey, sqliteTable, text, uniqueIndex } from "void/schema-d1";

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

// One row per player and item entry, however often they sent it. The flags say what the player saw of it.
export const itemSeen = sqliteTable(
  "item_seen",
  {
    entry: text("entry").notNull(),
    userId: text("user_id").notNull(),
    glint: integer("glint").notNull(),
    plain: integer("plain").notNull(),
    modVersion: text("mod_version").notNull(),
  },
  // The per-player cap counts a player's rows.
  (table) => [primaryKey({ columns: [table.entry, table.userId] }), index("item_seen_by_user").on(table.userId)],
);

// The skull texture a player saw on one skin of an item, "" for the item's own.
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

// One example per player and item. The agent's view leaves the user out.
export const itemSamples = sqliteTable(
  "item_samples",
  {
    entry: text("entry").notNull(),
    userId: text("user_id").notNull(),
    sample: text("sample", { mode: "json" }).notNull(),
  },
  (table) => [primaryKey({ columns: [table.entry, table.userId] })],
);
