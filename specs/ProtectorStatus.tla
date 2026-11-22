--------------------------- MODULE ProtectorStatus ---------------------------
(* @keep ProtectorStatus while the player is in The End. One atomic step is one callback. Chat, the tab list, and a
   world change arrive in any order.
   stage "none" is the null stage. spawned is spawnedAt > 0, set only by the emerge line. defeated is defeatedAt > 0,
   set by the defeat line only while the row is Spawned or the emerge line is still in force.
   inEnd is Area.END holding, so chat is delivered. A world change clears the tab first, so the End no longer holds
   and later chat is not delivered.
   The readout prefers defeated, then spawned. *)
EXTENDS FiniteSets

VARIABLES stage, spawned, defeated, inEnd

vars == <<stage, spawned, defeated, inEnd>>

Stages == {"none", "Resting", "Rising", "Spawned"}

Init ==
    /\ stage = "none"
    /\ spawned = FALSE
    /\ defeated = FALSE
    /\ inEnd = TRUE

\* @keep A kills-left change is a new row string, so the handler runs again even when the stage name stays Resting or Rising.
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

\* @keep Emerge has no guard on the current stage.
Emerge ==
    /\ inEnd
    /\ spawned' = TRUE
    /\ defeated' = FALSE
    /\ UNCHANGED <<stage, inEnd>>

Defeat ==
    /\ inEnd
    /\ stage = "Spawned" \/ spawned
    /\ defeated' = TRUE
    /\ UNCHANGED <<stage, spawned, inEnd>>

Leave ==
    /\ inEnd
    /\ stage' = "none"
    /\ spawned' = FALSE
    /\ defeated' = FALSE
    /\ inEnd' = FALSE

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

\* @keep Chat can still be ahead of the tab: emerge, then defeat, while the row is still Resting or Rising.
TabOwnsTheCycle ==
    stage \in {"Resting", "Rising"} /\ ~spawned => ~defeated

LeftClean ==
    ~inEnd => stage = "none" /\ ~spawned /\ ~defeated

Spec == Init /\ [][Next]_vars /\ WF_vars(Done)
=============================================================================
