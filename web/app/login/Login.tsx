"use client";

import { Button } from "@ui-sdk/react/button";
import { useEffect, useRef, useState } from "react";
import { begin, finish } from "./actions";
import type { Asked } from "./signIn";

type Step =
  | { readonly at: "loading" | "expired" | "cancelled" }
  | { readonly at: "confirm"; readonly asked: Asked; readonly unanswered: boolean }
  | { readonly at: "done"; readonly name: string };

const expired: Step = { at: "expired" };

/** The whole sign-in on one address: it trades the link's token, asks whose account it is, and says how it ended. */
export function Login() {
  const [step, setStep] = useState<Step>({ at: "loading" });
  const started = useRef(false);

  useEffect(() => {
    if (started.current) {
      return;
    }
    started.current = true;
    // The token rides in the fragment, which the browser sends to no server. It leaves the address bar once the
    // answer is in, because Next's router puts back an address that changes while the page is still starting.
    void begin(window.location.hash.slice(1))
      .then(
        (asked): Step => (asked === null ? expired : { at: "confirm", asked, unanswered: false }),
        () => expired,
      )
      .then((next) => {
        window.history.replaceState(null, "", window.location.pathname);
        setStep(next);
      });
  }, []);

  const answer = (choice: "continue" | "cancel", asked: Asked) => {
    setStep({ at: "loading" });
    void finish(choice, asked.user).then(
      (name) => {
        setStep(choice === "cancel" ? { at: "cancelled" } : name === null ? expired : { at: "done", name });
      },
      // The backend did not answer, and the session is still pending, so the player can try again.
      () => {
        setStep({ at: "confirm", asked, unanswered: true });
      },
    );
  };

  switch (step.at) {
    case "loading":
      return <p>Signing in...</p>;
    case "expired":
      return <p>That link has expired. Type /dsmod login in game for a new one.</p>;
    case "cancelled":
      return <p>Cancelled. Nobody was signed in.</p>;
    case "done":
      return (
        <>
          <h1>Signed in as {step.name}</h1>
          <p>You can close this tab.</p>
        </>
      );
    case "confirm":
      return (
        <>
          <h1>Sign in as {step.asked.name}?</h1>
          <p>Continue only if you just typed /dsmod login in game.</p>
          {step.unanswered && <p>The ds-mod backend did not answer. Try again in a moment.</p>}
          <Button
            onClick={() => {
              answer("continue", step.asked);
            }}
          >
            Continue
          </Button>
          <Button
            onClick={() => {
              answer("cancel", step.asked);
            }}
          >
            Cancel
          </Button>
        </>
      );
  }
}
