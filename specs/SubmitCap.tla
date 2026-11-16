------------------------------ MODULE SubmitCap ------------------------------
(***************************************************************************)
(* Two uploads of one contribution, from two Worker requests.              *)
(*                                                                         *)
(* contrib.ts:62 reads the runs.                                           *)
(* contrib.ts:66 refuses when the count is already twice `wanted`.         *)
(*   Both requests can pass this read before either insert.                *)
(* contrib.ts:70 calls insertSubmission with that cap.                     *)
(* routes/_store.ts:67 puts the file.                                      *)
(* routes/_store.ts:69-75 inserts the row only while the count is still     *)
(*   under the cap. One statement, so the second upload sees the first.    *)
(*   The unique index (schema.ts:29) is one row per player. These two      *)
(*   uploads are two players, so it does not fire.                         *)
(* routes/_store.ts:76-78 deletes the file when the insert kept nothing.   *)
(* contrib.ts:78 reads the runs again and may fill. Filling does not       *)
(*   change the count, so this model leaves that await out.                *)
(*                                                                         *)
(* The table already holds Cap - 1 runs, one under the cap.                *)
(***************************************************************************)
EXTENDS Naturals, FiniteSets

CONSTANT Uploads, Cap

VARIABLES pc, rows, files, kept

vars == <<pc, rows, files, kept>>

Init ==
    /\ pc = [u \in Uploads |-> "idle"]
    /\ rows = Cap - 1
    /\ files = {}
    /\ kept = {}

\* contrib.ts:62-68. The count this request saw is `rows` at this step.
Check(u) ==
    /\ pc[u] = "idle"
    /\ pc' = [pc EXCEPT ![u] = IF rows < Cap THEN "checked" ELSE "done"]
    /\ UNCHANGED <<rows, files, kept>>

\* routes/_store.ts:67
Put(u) ==
    /\ pc[u] = "checked"
    /\ files' = files \cup {u}
    /\ pc' = [pc EXCEPT ![u] = "put"]
    /\ UNCHANGED <<rows, kept>>

\* routes/_store.ts:69-78. The cap is in this step. A refused insert deletes its file.
Insert(u) ==
    /\ pc[u] = "put"
    /\ IF rows < Cap THEN
         /\ rows' = rows + 1
         /\ kept' = kept \cup {u}
         /\ UNCHANGED files
       ELSE
         /\ files' = files \ {u}
         /\ UNCHANGED <<rows, kept>>
    /\ pc' = [pc EXCEPT ![u] = "done"]

Done ==
    /\ \A u \in Uploads : pc[u] = "done"
    /\ UNCHANGED vars

Next ==
    \/ \E u \in Uploads : Check(u) \/ Put(u) \/ Insert(u)
    \/ Done

Spec == Init /\ [][Next]_vars

-----------------------------------------------------------------------------

TypeOK ==
    /\ rows \in Nat
    /\ kept \subseteq Uploads
    /\ files \subseteq Uploads
    /\ \A u \in Uploads : pc[u] \in {"idle", "checked", "put", "done"}

\* contrib.ts:49-52. Runs stay at most twice the number the request wanted.
AtMostTwice == rows <= Cap

\* routes/_store.ts:65-78. A row that was kept still has its file.
KeptHasFile == kept \subseteq files

=============================================================================
