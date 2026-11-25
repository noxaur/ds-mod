import localFont from "next/font/local";
import { Login } from "./Login";
import styles from "./login.module.css";

const pixel = localFont({ src: "./Monocraft.woff2", display: "block" });

const mix = (from: number, to: number, at: number) =>
  [16, 8, 0].map((shift) => Math.round(((from >> shift) & 255) * (1 - at) + ((to >> shift) & 255) * at));

const hex = (channels: number[]) => "#" + channels.map((channel) => channel.toString(16).padStart(2, "0")).join("");

const stops = [0xffbb00, 0xffffff, 0x6c68ff];
const name = "[ Dragen ]";
const brand = Array.from(name, (char, i) => {
  const at = (i / (name.length - 1)) * (stops.length - 1);
  const from = Math.min(Math.floor(at), stops.length - 2);
  const channels = mix(stops[from] ?? 0, stops[from + 1] ?? 0, at - from);
  return { char, color: hex(channels), shadow: hex(channels.map((channel) => channel >> 2)) };
});

export default function LoginPage() {
  return (
    <main className={`${pixel.className} ${styles.page}`}>
      <header className={styles.bar}>
        <span className={styles.brand} aria-label="Dragen">
          {brand.map(({ char, color, shadow }, i) => (
            <span key={i} aria-hidden style={{ color, textShadow: `var(--u) var(--u) 0 ${shadow}` }}>
              {char}
            </span>
          ))}
        </span>
      </header>
      <div className={styles.stage}>
        <Login />
      </div>
    </main>
  );
}
