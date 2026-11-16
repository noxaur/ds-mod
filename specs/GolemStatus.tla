--------------------------- MODULE GolemStatus ---------------------------
(***************************************************************************)
(* GolemStatus while the player is in The End. One atomic step is one      *)
(* callback. Chat, the tab list, and a world change arrive in any order.   *)
(*                                                                         *)
(* stage     GolemStatus.kt:37-45, after the TabList handler. "none" is    *)
(*           the null stage at lines 27 and 37.                            *)
(* spawned   spawnedAt > 0. Set only by the emerge line (lines 49-51).     *)
(* defeated  defeatedAt > 0. Set by the defeat line only while the row is  *)
(*           Spawned or the emerge line is still in force (lines 53-56).    *)
(* inEnd     Area.END holds, so chat is delivered (Game.kt:147). A world   *)
(*           change clears the tab first (Game.kt:134-138), so the End no  *)
(*           longer holds and later chat is not delivered.                 *)
(*                                                                         *)
(* The readout (GolemStatus.kt:59-78) prefers defeated, then spawned.      *)
(***************************************************************************)
EXTENDS FiniteSets

VARIABLES stage, spawned, defeated, inEnd

vars == <<stage, spawned, defeated, inEnd>>

Stages == {"none", "Resting", "Rising", "Spawned"}

Init ==
    /\ stage = "none"
    /\ spawned = FALSE
    /\ defeated = FALSE
    /\ inEnd = TRUE

\* GolemStatus.kt:32-47. A kills-left change is a new row string, so the handler
\* runs again even when the stage name stays Resting or Rising (line 34).
\* Lines 41-43 clear both clocks only when the new row is Resting, or the
\* previous stage was Spawned and the new one is not.
Tab(next) ==
    /\ inEnd
    /\ next \in {"Resting", "Rising", "Spawned"}
    /\ \/ next # stage
       \/ next \in {"Resting", "Rising"}
    /\ IF next = "Resting" \/ (stage = "Spawned" /\ next # "Spawned")
       THEN spawned' = FALSE /\ defeated' = FALSE
       ELSE UNCHANGED <<spawned, defeated>>
    /\ stage' = next
    /\ UNCHANGED inEnd

\* GolemStatus.kt:49-51. No guard on the current stage.
Emerge ==
    /\ inEnd
    /\ spawned' = TRUE
    /\ defeated' = FALSE
    /\ UNCHANGED <<stage, inEnd>>

\* GolemStatus.kt:53-56. The fight is still current when the row says Spawned, or the
\* emerge line set spawnedAt and the tab has not moved on (lines 41-43).
Defeat ==
    /\ inEnd
    /\ stage = "Spawned" \/ spawned
    /\ defeated' = TRUE
    /\ UNCHANGED <<stage, spawned, inEnd>>

\* GolemStatus.kt:26-30, and the End stops holding (Game.kt:134-138, Area.kt:40).
Leave ==
    /\ inEnd
    /\ stage' = "none"
    /\ spawned' = FALSE
    /\ defeated' = FALSE
    /\ inEnd' = FALSE

\* The next tab list says The End again, before any Protector row (lines 38-39).
Return ==
    /\ ~inEnd
    /\ inEnd' = TRUE
    /\ UNCHANGED <<stage, spawned, defeated>>

Done == ~inEnd /\ UNCHANGED vars

Next ==
    \/ \E next \in {"Resting", "Rising", "Spawned"} : Tab(next)
    \/ Emerge
    \/ Defeat
    \/ Leave
    \/ Return
    \/ Done

\* The comment at lines 40-41: the tab list moving on from the fight ends it.
\* A Resting or Rising row with no emerge clock is not a fight, so a defeat line
\* must not have been accepted. Chat can still be ahead of the tab: emerge, then
\* defeat, while the row has not left Resting or Rising yet.
TabOwnsTheCycle ==
    stage \in {"Resting", "Rising"} /\ ~spawned => ~defeated

\* Lines 26-30. Leaving the world drops the fight, and it stays dropped until
\* a new row or a new chat line in the next world.
LeftClean ==
    ~inEnd => stage = "none" /\ ~spawned /\ ~defeated

Spec == Init /\ [][Next]_vars /\ WF_vars(Done)
=============================================================================
