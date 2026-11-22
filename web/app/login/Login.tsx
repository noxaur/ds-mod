"use client";

import { Button } from "@ui-sdk/react/button";
import { type ReactNode, useEffect, useRef, useState } from "react";
import { begin, finish } from "./actions";
import { face } from "./head";
import styles from "./login.module.css";
import type { Asked } from "./signIn";

type Step =
  | { readonly at: "loading" | "expired" | "cancelled" }
  | { readonly at: "confirm"; readonly asked: Asked; readonly status: "asking" | "sending" | "unanswered" }
  | { readonly at: "done"; readonly asked: Asked };

const expired: Step = { at: "expired" };

export function Login() {
  const [step, setStep] = useState<Step>({ at: "loading" });
  const started = useRef(false);

  useEffect(() => {
    if (started.current) {
      return;
    }
    started.current = true;
    /* @keep The token rides in the fragment, which the browser sends to no server. It leaves the address bar once the
     * answer is in, because Next's router puts back an address that changes while the page is still starting. */
    void begin(window.location.hash.slice(1))
      .then(
        (asked): Step => (asked === null ? expired : { at: "confirm", asked, status: "asking" }),
        () => expired,
      )
      .then((next) => {
        window.history.replaceState(null, "", window.location.pathname);
        setStep(next);
      });
  }, []);

  const answer = (choice: "continue" | "cancel", asked: Asked) => {
    setStep({ at: "confirm", asked, status: "sending" });
    void finish(choice, asked.user).then(
      (name) => {
        setStep(
          choice === "cancel"
            ? { at: "cancelled" }
            : name === null
              ? expired
              : { at: "done", asked: { ...asked, name } },
        );
      },
      () => {
        setStep({ at: "confirm", asked, status: "unanswered" });
      },
    );
  };

  switch (step.at) {
    case "loading":
      return (
        <Panel title="Signing in...">
          <div className={styles.well}>
            <i className={`${styles.bone} ${styles.headBone}`} />
            <i className={styles.bone} />
          </div>
          <Choices onAnswer={null} />
        </Panel>
      );
    case "expired":
      return (
        <Panel title="That link has expired" icon={styles.warn}>
          <p className={styles.muted}>
            Type <Command /> in game again.
          </p>
        </Panel>
      );
    case "cancelled":
      return (
        <Panel title="Cancelled" icon={styles.cross}>
          <p className={styles.muted}>Nobody was signed in.</p>
        </Panel>
      );
    case "done":
      return (
        <Panel title="Signed in">
          <Player asked={step.asked} signedIn />
          <p className={styles.muted}>You can close this tab.</p>
        </Panel>
      );
    case "confirm":
      return (
        <Panel title="Sign in as this player?">
          <Player asked={step.asked} />
          {step.status === "unanswered" ? (
            <p className={`${styles.bad} ${styles.title}`}>
              <i className={`${styles.icon} ${styles.warn}`} />
              The backend did not answer. Try again.
            </p>
          ) : (
            <p className={styles.muted}>
              Continue only if you just typed <Command /> in game.
            </p>
          )}
          <Choices
            onAnswer={
              step.status === "sending"
                ? null
                : (choice) => {
                    answer(choice, step.asked);
                  }
            }
          />
        </Panel>
      );
  }
}

function Panel({ title, icon, children }: { title: string; icon?: string; children: ReactNode }) {
  return (
    <section className={styles.panel}>
      <div className={styles.face}>
        <h1 className={styles.title}>
          {icon !== undefined && <i className={`${styles.icon} ${icon}`} />}
          {title}
        </h1>
        <hr className={styles.rule} />
        {children}
      </div>
    </section>
  );
}

function Player({ asked, signedIn = false }: { asked: Asked; signedIn?: boolean }) {
  return (
    <div className={styles.well}>
      {asked.skin !== null && <Head skin={asked.skin} />}
      <span className={styles.value}>{asked.name}</span>
      {signedIn && <i className={`${styles.icon} ${styles.check} ${styles.good}`} />}
    </div>
  );
}

function Head({ skin }: { skin: string }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const image = new Image();
    image.crossOrigin = "anonymous";
    image.onload = () => {
      const reader = document.createElement("canvas");
      reader.width = image.width;
      reader.height = image.height;
      const read = reader.getContext("2d");
      const draw = canvas.current?.getContext("2d") ?? null;
      if (read === null || draw === null) {
        return;
      }
      read.drawImage(image, 0, 0);
      draw.putImageData(new ImageData(face(read.getImageData(0, 0, image.width, image.height)), 8, 8), 0, 0);
    };
    image.src = skin;
  }, [skin]);
  return <canvas ref={canvas} className={styles.head} width={8} height={8} />;
}

function Choices({ onAnswer }: { onAnswer: ((choice: "continue" | "cancel") => void) | null }) {
  const off = onAnswer === null;
  return (
    <div className={styles.choices}>
      <Button className={styles.button} disabled={off} onClick={() => onAnswer?.("continue")}>
        <span>Continue</span>
      </Button>
      <Button className={styles.button} disabled={off} onClick={() => onAnswer?.("cancel")}>
        <span>Cancel</span>
      </Button>
    </div>
  );
}

function Command() {
  return <code className={styles.command}>/dsmod login</code>;
}
