------------------------------ MODULE SubmitCap ------------------------------
(***************************************************************************)
(* Two uploads of one contribution, from two Worker requests, and an       *)
(* agent who may close the request at any moment.                         *)
(*                                                                         *)
(* contrib.ts:62 reads the runs.                                           *)
(* contrib.ts:67 refuses when the request is closed or the count is       *)
(*   already twice `wanted`. Both requests can pass this read before       *)
(*   either insert, and the agent can close the request after it.          *)
(* contrib.ts:71 calls insertSubmission with that cap.                     *)
(* routes/_store.ts:67 puts the file.                                      *)
(* routes/_store.ts:71-76 inserts the row only while the count is still    *)
(*   under the cap and the request is not closed. One statement, so the    *)
(*   second upload sees the first, and an upload sees a close.             *)
(*   The unique index (schema.ts:29) is one row per player. These two      *)
(*   uploads are two players, so it does not fire.                         *)
(* routes/_store.ts:78-80 deletes the file when the insert kept nothing.  *)
(* contrib.ts:79 reads the runs again and may fill. Filling does not       *)
(*   change the count, so this model leaves that await out.                *)
(*                                                                         *)
(* The table already holds Cap - 1 runs, one under the cap.                *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets

CONSTANT Uploads, Cap

VARIABLES pc, rows, files, kept, closed, atClose

vars == <<pc, rows, files, kept, closed, atClose>>

Init ==
    /\ pc = [u \in Uploads |-> "idle"]
    /\ rows = Cap - 1
    /\ files = {}
    /\ kept = {}
    /\ closed = FALSE
    /\ atClose = 0

\* contrib.ts:62-69. The status and the count this request saw is `rows` at this step.
Check(u) ==
    /\ pc[u] = "idle"
    /\ pc' = [pc EXCEPT ![u] = IF rows < Cap /\ ~closed THEN "checked" ELSE "done"]
    /\ UNCHANGED <<rows, files, kept, closed, atClose>>

\* routes/_store.ts:67
Put(u) ==
    /\ pc[u] = "checked"
    /\ files' = files \cup {u}
    /\ pc' = [pc EXCEPT ![u] = "put"]
    /\ UNCHANGED <<rows, kept, closed, atClose>>

\* routes/_store.ts:71-80. The cap and the request's status are in this step. A refused insert deletes its file.
Insert(u) ==
    /\ pc[u] = "put"
    /\ IF rows < Cap /\ ~closed THEN
         /\ rows' = rows + 1
         /\ kept' = kept \cup {u}
         /\ UNCHANGED files
       ELSE
         /\ files' = files \ {u}
         /\ UNCHANGED <<rows, kept>>
    /\ pc' = [pc EXCEPT ![u] = "done"]
    /\ UNCHANGED <<closed, atClose>>

\* contrib.ts:85-88, close. It does not wait for an upload in flight.
Close ==
    /\ ~closed
    /\ closed' = TRUE
    /\ atClose' = rows
    /\ UNCHANGED <<pc, rows, files, kept>>

Done ==
    /\ \A u \in Uploads : pc[u] = "done"
    /\ UNCHANGED vars

Next ==
    \/ \E u \in Uploads : Check(u) \/ Put(u) \/ Insert(u)
    \/ Close
    \/ Done

Spec == Init /\ [][Next]_vars

-----------------------------------------------------------------------------

TypeOK ==
    /\ rows \in Nat
    /\ kept \subseteq Uploads
    /\ files \subseteq Uploads
    /\ \A u \in Uploads : pc[u] \in {"idle", "checked", "put", "done"}
    /\ closed \in BOOLEAN
    /\ atClose \in Nat

\* contrib.ts:49-52. Runs stay at most twice the number the request wanted.
AtMostTwice == rows <= Cap

\* routes/_store.ts:65-81. A row that was kept still has its file.
KeptHasFile == kept \subseteq files

\* api.ts: a closed request takes no run, even one whose upload passed the check before the close.
ClosedTakesNoRun == closed => rows = atClose

=============================================================================
