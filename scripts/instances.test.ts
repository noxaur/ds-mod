import assert from "node:assert/strict";
import { existsSync, mkdirSync, mkdtempSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { DatabaseSync } from "node:sqlite";
import { test } from "node:test";
import { instances, modsFolder, type Machine } from "./instances.ts";

const fabricPack = JSON.stringify({ components: [{ uid: "net.minecraft" }, { uid: "net.fabricmc.fabric-loader" }] });
const vanillaPack = JSON.stringify({ components: [{ uid: "net.minecraft" }] });

const machine = (files: Record<string, string>, platform: NodeJS.Platform = "linux"): Machine => {
  const home = mkdtempSync(join(tmpdir(), "instances-"));
  for (const [file, text] of Object.entries(files)) {
    mkdirSync(join(home, file.endsWith("/") ? file : join(file, "..")), { recursive: true });
    if (!file.endsWith("/")) writeFileSync(join(home, file), text);
  }
  return { home, platform, env: {} };
};

const database = (file: string, statements: string[]) => {
  mkdirSync(join(file, ".."), { recursive: true });
  const db = new DatabaseSync(file);
  for (const statement of statements) db.exec(statement);
  db.close();
};

test("finds the official launcher's folder and the Fabric instances of Prism, MultiMC, CurseForge, ATLauncher, GDLauncher and Dawn", () => {
  const at = machine({
    ".minecraft/": "",
    ".local/share/PrismLauncher/instances/Skyblock/mmc-pack.json": fabricPack,
    ".local/share/PrismLauncher/instances/Skyblock/minecraft/": "",
    ".local/share/PrismLauncher/instances/Vanilla/mmc-pack.json": vanillaPack,
    ".local/share/PrismLauncher/instances/Vanilla/minecraft/": "",
    ".var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/Flat/mmc-pack.json": fabricPack,
    ".var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/Flat/.minecraft/": "",
    "MultiMC/multimc.cfg": "InstanceDir=packs\n",
    "MultiMC/packs/Old/mmc-pack.json": fabricPack,
    "MultiMC/packs/Old/.minecraft/": "",
    "curseforge/minecraft/Instances/Pack/minecraftinstance.json": JSON.stringify({ baseModLoader: { name: "fabric-0.17.2-26.2" } }),
    "curseforge/minecraft/Instances/Forged/minecraftinstance.json": JSON.stringify({ baseModLoader: { name: "forge-60.0.1" } }),
    ".local/share/atlauncher/instances/Atl/instance.json": JSON.stringify({ launcher: { loaderVersion: { type: "Fabric" } } }),
    ".local/share/gdlauncher_carbon/data/instances/Gd/instance.json": JSON.stringify({ game_configuration: { version: { modloaders: [{ type: "Fabric" }] } } }),
    ".local/share/gdlauncher_carbon/data/instances/Gd/instance/": "",
    ".dawn/profiles/dawn-fabric/profile.json": JSON.stringify({ profile: { loader: { kind: "fabric" } } }),
    ".dawn/profiles/dawn-fabric/.minecraft/": "",
  });
  assert.deepEqual(
    instances(at),
    new Map([
      ["minecraft", join(at.home, ".minecraft/mods")],
      ["prism/Skyblock", join(at.home, ".local/share/PrismLauncher/instances/Skyblock/minecraft/mods")],
      ["prism/Flat", join(at.home, ".var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/Flat/.minecraft/mods")],
      ["multimc/Old", join(at.home, "MultiMC/packs/Old/.minecraft/mods")],
      ["curseforge/Pack", join(at.home, "curseforge/minecraft/Instances/Pack/mods")],
      ["atlauncher/Atl", join(at.home, ".local/share/atlauncher/instances/Atl/mods")],
      ["gdlauncher/Gd", join(at.home, ".local/share/gdlauncher_carbon/data/instances/Gd/instance/mods")],
      ["dawn/dawn-fabric", join(at.home, ".dawn/profiles/dawn-fabric/.minecraft/mods")],
    ]),
  );
});

test("picks the game folder each launcher loads, before the game has made it", () => {
  const at = machine({
    ".local/share/PrismLauncher/instances/Both/mmc-pack.json": fabricPack,
    ".local/share/PrismLauncher/instances/Both/minecraft/": "",
    ".local/share/PrismLauncher/instances/Both/.minecraft/": "",
    ".local/share/PrismLauncher/instances/Fresh/mmc-pack.json": fabricPack,
    "MultiMC/multimc.cfg": "",
    "MultiMC/instances/Both/mmc-pack.json": fabricPack,
    "MultiMC/instances/Both/minecraft/": "",
    "MultiMC/instances/Both/.minecraft/": "",
    "MultiMC/instances/Fresh/mmc-pack.json": fabricPack,
  });
  assert.deepEqual(
    instances(at),
    new Map([
      ["prism/Both", join(at.home, ".local/share/PrismLauncher/instances/Both/minecraft/mods")],
      ["prism/Fresh", join(at.home, ".local/share/PrismLauncher/instances/Fresh/minecraft/mods")],
      ["multimc/Both", join(at.home, "MultiMC/instances/Both/.minecraft/mods")],
      ["multimc/Fresh", join(at.home, "MultiMC/instances/Fresh/.minecraft/mods")],
    ]),
  );
});

test("keeps the first data folder's instance when another has one of the same name, and follows a linked instance", () => {
  const at = machine({
    ".local/share/PrismLauncher/instances/Skyblock/mmc-pack.json": fabricPack,
    ".var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/Skyblock/mmc-pack.json": fabricPack,
    "packs/Linked/mmc-pack.json": fabricPack,
  });
  symlinkSync(join(at.home, "packs/Linked"), join(at.home, ".local/share/PrismLauncher/instances/Linked"));
  assert.deepEqual(
    instances(at),
    new Map([
      ["prism/Skyblock", join(at.home, ".local/share/PrismLauncher/instances/Skyblock/minecraft/mods")],
      ["prism/Linked", join(at.home, ".local/share/PrismLauncher/instances/Linked/minecraft/mods")],
    ]),
  );
});

test("follows the instance folder that Prism's and CurseForge's settings name", () => {
  const at = machine({ "AppData/Roaming/PrismLauncher/prismlauncher.cfg": "[General]\nInstanceDir=elsewhere\n", "AppData/Roaming/PrismLauncher/elsewhere/Skyblock/mmc-pack.json": fabricPack, "AppData/Roaming/PrismLauncher/elsewhere/Skyblock/minecraft/": "" }, "win32");
  const moved = join(at.home, "D/modding");
  mkdirSync(join(at.home, "AppData/Roaming/CurseForge"), { recursive: true });
  writeFileSync(join(at.home, "AppData/Roaming/CurseForge/storage.json"), JSON.stringify({ "minecraft-settings": JSON.stringify({ minecraftRoot: moved }) }));
  mkdirSync(join(moved, "Instances/Pack"), { recursive: true });
  writeFileSync(join(moved, "Instances/Pack/minecraftinstance.json"), JSON.stringify({ baseModLoader: { name: "quilt-0.29.0" } }));
  assert.deepEqual(
    instances(at),
    new Map([
      ["prism/Skyblock", join(at.home, "AppData/Roaming/PrismLauncher/elsewhere/Skyblock/minecraft/mods")],
      ["curseforge/Pack", join(moved, "Instances/Pack/mods")],
    ]),
  );
});

test("reads the Fabric instances of the Modrinth App from its database, in the folder its settings name", () => {
  const at = machine({ "moved/profiles/fabric-pack/": "", "moved/profiles/vanilla/": "" });
  database(join(at.home, ".local/share/ModrinthApp/app.db"), [
    "CREATE TABLE settings (custom_dir TEXT)",
    `INSERT INTO settings VALUES ('${join(at.home, "moved")}')`,
    "CREATE TABLE instances (id TEXT, name TEXT, path TEXT, applied_content_set_id TEXT)",
    "CREATE TABLE instance_content_sets (id TEXT, instance_id TEXT, loader TEXT)",
    "INSERT INTO instances VALUES ('a', 'Fabric Pack', 'fabric-pack', 'sa'), ('b', 'Vanilla', 'vanilla', 'sb')",
    "INSERT INTO instance_content_sets VALUES ('sa', 'a', 'fabric'), ('sb', 'b', 'vanilla')",
  ]);
  assert.deepEqual(instances(at), new Map([["modrinth/fabric-pack", join(at.home, "moved/profiles/fabric-pack/mods")]]));
});

test("puts a Lunar profile's mods in the loader and version folder Lunar passes to Fabric", () => {
  const at = machine({ ".lunarclient/profiles/lunar-fabric/": "", "own/": "" });
  database(join(at.home, ".lunarclient/db/profiles.db"), [
    "CREATE TABLE profiles (path TEXT, type TEXT, game_version TEXT, loaders TEXT, lunar_module TEXT, game_directory TEXT, mods_directory TEXT)",
    `INSERT INTO profiles VALUES
      ('lunar-fabric', 'lunar', '26.2', '["ichor","fabric"]', 'fabric', NULL, NULL),
      ('own-folder', 'vanilla', '26.2', '["fabric"]', 'vanilla-fabric', '${join(at.home, "own")}', NULL),
      ('pvp', 'lunar', '1.8.9', '["ichor"]', NULL, NULL, NULL)`,
  ]);
  assert.deepEqual(
    instances(at),
    new Map([
      ["lunar/lunar-fabric", join(at.home, ".lunarclient/profiles/lunar-fabric/mods/fabric-26.2")],
      ["lunar/own-folder", join(at.home, "own/mods")],
    ]),
  );
});

test("makes a named instance's mods folder, and takes a path to a game folder or its mods folder", () => {
  const at = machine({ ".local/share/PrismLauncher/instances/Skyblock/mmc-pack.json": fabricPack, ".local/share/PrismLauncher/instances/Skyblock/minecraft/": "", "elsewhere/mods/": "" });
  const skyblock = join(at.home, ".local/share/PrismLauncher/instances/Skyblock/minecraft/mods");
  assert.equal(modsFolder("prism/Skyblock", at.home, at), skyblock);
  assert.ok(existsSync(skyblock));
  assert.equal(modsFolder("elsewhere", at.home, at), join(at.home, "elsewhere/mods"));
  assert.equal(modsFolder("elsewhere/mods", at.home, at), join(at.home, "elsewhere/mods"));
});

test("lists the instances when the argument names neither an instance nor a game folder", () => {
  const at = machine({ ".local/share/PrismLauncher/instances/Skyblock/mmc-pack.json": fabricPack, ".local/share/PrismLauncher/instances/Skyblock/minecraft/": "" });
  assert.throws(() => modsFolder("prism/Skyblok", at.home, at), { message: "prism/Skyblok is neither an instance nor a game folder that holds mods. The instances are: prism/Skyblock." });
});
