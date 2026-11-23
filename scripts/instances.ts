import { existsSync, mkdirSync, readdirSync, readFileSync } from "node:fs";
import { homedir } from "node:os";
import { basename, isAbsolute, join, resolve } from "node:path";
import { DatabaseSync } from "node:sqlite";

export type Machine = { readonly home: string; readonly platform: NodeJS.Platform; readonly env: NodeJS.ProcessEnv };

type Instance = { readonly name: string; readonly game: string; readonly mods?: string };

const thisMachine: Machine = { home: homedir(), platform: process.platform, env: process.env };

const read = (file: string) => {
  try {
    return readFileSync(file, "utf8");
  } catch {
    return "";
  }
};

const parse = (text: string): any => {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
};

const json = (file: string) => parse(read(file));

const rows = (file: string, query: string): any[] => {
  if (!existsSync(file)) return [];
  try {
    const db = new DatabaseSync(file, { readOnly: true });
    try {
      return db.prepare(query).all();
    } finally {
      db.close();
    }
  } catch {
    return [];
  }
};

const folders = (dir: string) => {
  try {
    return readdirSync(dir, { withFileTypes: true })
      .filter((entry) => entry.isDirectory())
      .map((entry) => ({ name: entry.name, path: join(dir, entry.name) }));
  } catch {
    return [];
  }
};

const firstExisting = (paths: string[]) => paths.find(existsSync);

const loadsFabric = (loader: unknown) => typeof loader === "string" && /^(legacy)?(fabric|quilt)/i.test(loader);

const appDirs = ({ home, platform, env }: Machine, app: string, flatpak?: string) => {
  if (platform === "win32") return [join(env["APPDATA"] ?? join(home, "AppData", "Roaming"), app)];
  if (platform === "darwin") return [join(home, "Library", "Application Support", app)];
  const sandbox = flatpak === undefined ? [] : [join(home, ".var", "app", flatpak, "data", app), join(home, ".var", "app", flatpak, "config", app)];
  return [join(env["XDG_DATA_HOME"] ?? join(home, ".local", "share"), app), join(env["XDG_CONFIG_HOME"] ?? join(home, ".config"), app), ...sandbox];
};

const multiMcFormat = (data: string, cfg: string): Instance[] => {
  const setting = /^InstanceDir=(.+)$/m.exec(read(join(data, cfg)))?.[1]?.trim() ?? "instances";
  return folders(isAbsolute(setting) ? setting : join(data, setting)).flatMap(({ name, path }) => {
    const pack = json(join(path, "mmc-pack.json"));
    const game = firstExisting([join(path, ".minecraft"), join(path, "minecraft")]);
    const fabric = pack?.components?.some((component: { uid?: string }) => component.uid === "net.fabricmc.fabric-loader" || component.uid === "org.quiltmc.quilt-loader");
    return fabric && game !== undefined ? [{ name, game }] : [];
  });
};

const launchers: Record<string, (machine: Machine) => Instance[]> = {
  prism: (machine) =>
    [...(machine.env["PRISMLAUNCHER_DATA_DIR"] ? [machine.env["PRISMLAUNCHER_DATA_DIR"]] : []), ...appDirs(machine, "PrismLauncher", "org.prismlauncher.PrismLauncher"), join(machine.home, "snap", "prismlauncher", "common")].flatMap(
      (data) => multiMcFormat(data, "prismlauncher.cfg"),
    ),
  multimc: (machine) =>
    (machine.platform === "darwin" ? ["/Applications", join(machine.home, "Applications")].map((apps) => join(apps, "MultiMC.app", "Data")) : machine.platform === "win32" ? [] : [join(machine.env["XDG_DATA_HOME"] ?? join(machine.home, ".local", "share"), "multimc")])
      .concat(join(machine.home, "MultiMC"))
      .filter((data) => existsSync(join(data, "multimc.cfg")))
      .flatMap((data) => multiMcFormat(data, "multimc.cfg")),
  modrinth: (machine) =>
    appDirs(machine, "ModrinthApp", "com.modrinth.ModrinthApp").flatMap((data) => {
      const db = join(data, "app.db");
      const root = rows(db, "SELECT custom_dir FROM settings LIMIT 1")[0]?.custom_dir?.trim() || data;
      const instances = rows(db, "SELECT i.path, cs.loader FROM instances i LEFT JOIN instance_content_sets cs ON cs.id = i.applied_content_set_id AND cs.instance_id = i.id");
      return instances.filter((row) => loadsFabric(row.loader)).map((row) => ({ name: row.path, game: join(root, "profiles", row.path) }));
    }),
  curseforge: (machine) => {
    const configured = appDirs(machine, "CurseForge").flatMap((data) => {
      const settings = json(join(data, "storage.json"))?.["minecraft-settings"];
      const root = typeof settings === "string" ? parse(settings)?.minecraftRoot : undefined;
      return typeof root === "string" ? [join(root, "Instances")] : [];
    });
    const root = firstExisting([...configured, join(machine.home, "curseforge", "minecraft", "Instances"), join(machine.home, "Documents", "curseforge", "minecraft", "Instances")]);
    return root === undefined
      ? []
      : folders(root).flatMap(({ name, path }) => {
          const manifest = json(join(path, "minecraftinstance.json"));
          return loadsFabric(manifest?.baseModLoader?.name) ? [{ name, game: path }] : [];
        });
  },
  atlauncher: ({ home, platform, env }) =>
    (platform === "darwin"
      ? ["/Applications", join(home, "Applications")].map((apps) => join(apps, "ATLauncher.app", "Contents", "Java"))
      : platform === "win32"
        ? [join(env["APPDATA"] ?? join(home, "AppData", "Roaming"), "ATLauncher")]
        : [join(env["XDG_DATA_HOME"] ?? join(home, ".local", "share"), "atlauncher"), join(home, ".var", "app", "com.atlauncher.ATLauncher", "data")]
    )
      .concat(join(home, "ATLauncher"))
      .flatMap((data) =>
        folders(join(data, "instances")).flatMap(({ name, path }) => {
          const launcher = json(join(path, "instance.json"))?.launcher;
          return loadsFabric(launcher?.loaderVersion?.type) ? [{ name, game: path }] : [];
        }),
      ),
  gdlauncher: (machine) =>
    appDirs(machine, "gdlauncher_carbon").flatMap((data) =>
      folders(join(read(join(data, "runtime_path_override")).trim() || join(data, "data"), "instances")).flatMap(({ name, path }) => {
        const manifest = json(join(path, "instance.json"));
        const fabric = manifest?.game_configuration?.version?.modloaders?.some((loader: { type?: string }) => loadsFabric(loader.type));
        return fabric ? [{ name, game: join(path, "instance") }] : [];
      }),
    ),
  dawn: (machine) =>
    [...appDirs(machine, ".dawn"), join(machine.home, ".dawn")].flatMap((data) =>
      folders(join(data, "profiles")).flatMap(({ name, path }) => {
        const profile = json(join(path, "profile.json"))?.profile;
        const game = firstExisting([join(path, ".minecraft"), join(path, "minecraft")]);
        return loadsFabric(profile?.loader?.kind) && game !== undefined ? [{ name, game }] : [];
      }),
    ),
  lunar: ({ home, env }) => {
    const root = env["LUNAR_CLIENT_DIRECTORY"] ?? join(home, ".lunarclient");
    return rows(join(root, "db", "profiles.db"), "SELECT path, type, game_version, loaders, lunar_module, game_directory, mods_directory FROM profiles")
      .filter((row) => parse(row.loaders)?.some?.(loadsFabric))
      .map((row) => {
        const game = row.game_directory || join(root, "profiles", row.path);
        const mods = row.mods_directory ?? join(game, "mods");
        const loader = /quilt/.test(row.lunar_module ?? "") ? "quilt" : /fabric/.test(row.lunar_module ?? "") ? "fabric" : "ichor";
        return { name: row.path, game, mods: row.type === "lunar" ? join(mods, `${loader}-${row.game_version}`) : mods };
      });
  },
};

export const instances = (machine: Machine = thisMachine): Map<string, string> => {
  const { home, platform, env } = machine;
  const official = platform === "win32" ? join(env["APPDATA"] ?? join(home, "AppData", "Roaming"), ".minecraft") : platform === "darwin" ? join(home, "Library", "Application Support", "minecraft") : join(home, ".minecraft");
  const found = new Map<string, string>();
  if (existsSync(official)) found.set("minecraft", join(official, "mods"));
  for (const [launcher, find] of Object.entries(launchers)) {
    for (const { name, game, mods } of find(machine)) {
      if (existsSync(game)) found.set(`${launcher}/${name}`, mods ?? join(game, "mods"));
    }
  }
  return found;
};

export const modsFolder = (argument: string, cwd: string, machine: Machine = thisMachine): string => {
  const named = instances(machine);
  const mods = named.get(argument);
  if (mods !== undefined) {
    mkdirSync(mods, { recursive: true });
    return mods;
  }
  const folder = resolve(cwd, argument);
  const given = basename(folder) === "mods" ? folder : join(folder, "mods");
  if (existsSync(given)) return given;
  throw new Error(
    named.size > 0
      ? `${argument} is neither an instance nor a game folder that holds mods. The instances are: ${[...named.keys()].join(", ")}.`
      : `${argument} is not a game folder that holds mods, and no Fabric instance was found in the official launcher, Prism, MultiMC, Modrinth, CurseForge, ATLauncher, GDLauncher, Dawn or Lunar. Pass the game folder.`,
  );
};

if (import.meta.main) {
  const [argument] = process.argv.slice(2);
  try {
    process.stdout.write(argument === undefined ? [...instances().keys()].map((name) => `${name}\n`).join("") : modsFolder(argument, process.env["MISE_ORIGINAL_CWD"] ?? process.cwd()));
  } catch (error) {
    process.stderr.write(`${(error as Error).message}\n`);
    process.exit(1);
  }
}
