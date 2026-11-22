------------------------------ MODULE SubmitCap ------------------------------
(* @keep Two uploads of one contribution, from two Worker requests, and an agent who may close the request at any moment.
   Both requests can pass the cap check in contrib.ts before either insert, and the agent can close the request after it.
   The insert in routes/_store.ts is one statement that checks the cap and the status, so the second upload sees the first,
   and an upload sees a close. A refused insert deletes its file.
   The unique index is one row per player. These two uploads are two players, so it does not fire.
   Filling after the second read does not change the count, so the model leaves that await out.
   Cap is twice the number the request wanted, and the table already holds Cap - 1 runs. *)
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

Check(u) ==
    /\ pc[u] = "idle"
    /\ pc' = [pc EXCEPT ![u] = IF rows < Cap /\ ~closed THEN "checked" ELSE "done"]
    /\ UNCHANGED <<rows, files, kept, closed, atClose>>

Put(u) ==
    /\ pc[u] = "checked"
    /\ files' = files \cup {u}
    /\ pc' = [pc EXCEPT ![u] = "put"]
    /\ UNCHANGED <<rows, kept, closed, atClose>>

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

\* @keep Close does not wait for an upload in flight.
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

AtMostTwice == rows <= Cap

KeptHasFile == kept \subseteq files

ClosedTakesNoRun == closed => rows = atClose

=============================================================================
