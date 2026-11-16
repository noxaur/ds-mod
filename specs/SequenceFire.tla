---------------------------- MODULE SequenceFire ----------------------------
(***************************************************************************)
(* Command binds. One sequence is waiting out a longer one, and the client *)
(* tick may finish it. The player can open chat, focus a text field, hold  *)
(* F3, or turn Command Binds off while it waits.                           *)
(*                                                                         *)
(* player   presses a key (BindMatcher.press, CommandKeys.input).          *)
(* screen   moves between the world, a menu, and a place where no bind     *)
(*          may fire. CommandKeys.where returns null for that place, and   *)
(*          for F3 held. Command Binds off is the same: the tick passes    *)
(*          no place (CommandKeys.kt:33). Opening one does not call press. *)
(* tick     calls BindMatcher.idle. Idle sends only while where() covers   *)
(*          the stored typedWhere, and clears the sequence otherwise       *)
(*          (BindMatcher.kt:58-61, CommandKeys.kt:30-34).                   *)
(*                                                                         *)
(* Two binds stand for the lists in the tests: F6 then F7, and F6 F7 F6.   *)
(* Keys are 1 and 2. MAX_KEYS is 3 because neither bind is longer.         *)
(* Gap is the sequence gap. now stops at Horizon so the clock is finite.   *)
(***************************************************************************)
EXTENDS Naturals, Sequences

CONSTANT Gap, Horizon

VARIABLES
    screen,     \* "world" | "menu" | "none"  CommandKeys.where, none means it returned null
    typed,      \* BindMatcher.typed
    typedAt,    \* BindMatcher.typedAt
    typedWhere, \* BindMatcher.typedWhere, "world" | "menu"
    now,        \* the clock the tick and the presses share
    sentOff,    \* a command was sent while where() is null
    fires       \* how many commands send() has sent

vars == <<screen, typed, typedAt, typedWhere, now, sentOff, fires>>

Short == <<1, 2>>
Long == <<1, 2, 1>>

Init ==
    /\ screen = "world"
    /\ typed = <<>>
    /\ typedAt = 0
    /\ typedWhere = "world"
    /\ now = 0
    /\ sentOff = FALSE
    /\ fires = 0

\* BindMatcher.press lines 35-36. A sequence longer than MAX_KEYS drops its oldest key.
Cap(seq) == IF Len(seq) > 3 THEN Tail(seq) ELSE seq

Ends(seq, bind) ==
    Len(bind) <= Len(seq) /\ SubSeq(seq, Len(seq) - Len(bind) + 1, Len(seq)) = bind

\* BindMatcher.continues, lines 67-69, for a longer bind that starts with what was just typed.
Continues(seq) ==
    \/ /\ Len(seq) >= 1
       /\ SubSeq(seq, Len(seq), Len(seq)) = SubSeq(Long, 1, 1)
    \/ /\ Len(seq) >= 2
       /\ SubSeq(seq, Len(seq) - 1, Len(seq)) = SubSeq(Long, 1, 2)

\* BindMatcher.finish, line 64: the longest sequence that ended, or nothing.
Fires(seq) == Ends(seq, Long) \/ Ends(seq, Short)

\* Where.covers. where() is WORLD or MENUS, never BOTH, so the place must be the stored one.
Covers(place, stored) == place /= "none" /\ place = stored

\* CommandKeys.input PRESS (lines 43-45), then BindMatcher.press (lines 19-41).
Press(k) ==
    /\ now < Horizon
    /\ screen' = screen
    /\ now' = now
    /\ LET next == Cap(Append(typed, k))
           blocked == screen = "none"
           keep == ~blocked /\ Continues(next)
           send == ~blocked /\ ~Continues(next) /\ Fires(next)
       IN /\ typed' = IF blocked THEN <<>> ELSE IF keep THEN next ELSE <<>>
          /\ typedAt' = IF blocked THEN typedAt ELSE now
          /\ typedWhere' = IF keep THEN screen ELSE typedWhere
          /\ fires' = IF send THEN fires + 1 ELSE fires
          /\ sentOff' = IF send /\ screen = "none" THEN TRUE ELSE sentOff

\* No listener clears typed when the screen changes. where() is read on the tick.
To(place) ==
    /\ screen' = place
    /\ UNCHANGED <<typed, typedAt, typedWhere, now, sentOff, fires>>

\* ClientTickEvents.END_CLIENT_TICK (CommandKeys.kt:30-34), then BindMatcher.idle (lines 58-64).
Tick ==
    /\ now < Horizon
    /\ now' = now + 1
    /\ screen' = screen
    /\ typedAt' = typedAt
    /\ typedWhere' = typedWhere
    /\ LET blocked == ~Covers(screen, typedWhere)
           due == ~blocked /\ typed /= <<>> /\ now' - typedAt >= Gap
           send == due /\ Fires(typed)
       IN /\ typed' = IF blocked \/ due THEN <<>> ELSE typed
          /\ fires' = IF send THEN fires + 1 ELSE fires
          /\ sentOff' = IF send /\ screen = "none" THEN TRUE ELSE sentOff

Done == now >= Horizon /\ UNCHANGED vars

Next ==
    \/ Press(1) \/ Press(2)
    \/ To("world") \/ To("menu") \/ To("none")
    \/ Tick
    \/ Done

\* input.md: a bind fires only in the world or in a menu. CommandKeys.send must not run while where() is null.
NoSendOffScreen == ~sentOff

\* BindMatcher.press drops the oldest key past MAX_KEYS, so the buffer stays within it.
SequenceBounded == Len(typed) <= 3

Spec == Init /\ [][Next]_vars
=============================================================================
