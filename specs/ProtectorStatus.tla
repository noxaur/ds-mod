--------------------------- MODULE ProtectorStatus ---------------------------
(* @keep Chat, the tab list and a world change arrive in any order. *)
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

TabOwnsTheCycle ==
    stage \in {"Resting", "Rising"} /\ ~spawned => ~defeated

LeftClean ==
    ~inEnd => stage = "none" /\ ~spawned /\ ~defeated

Spec == Init /\ [][Next]_vars /\ WF_vars(Done)
=============================================================================
