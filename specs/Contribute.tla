---------------------------- MODULE Contribute ----------------------------
(***************************************************************************)
(* A player's runs of contribution requests, from Accept to the upload,    *)
(* across the client thread, the io thread, the backend's worker and the   *)
(* disk, which outlives a quit or a crash.                                 *)
(*                                                                         *)
(* client  accepts a run, starts it with the key, finishes it (last step   *)
(*         or /dsmod submit) or discards it (/dsmod cancel, time out,      *)
(*         disconnect, quit), and asks for a resend (/dsmod submit).       *)
(* io      one queue: the startup sweep, deleting a discarded raw capture, *)
(*         and sealing a finished one (raw -> .part -> .gz, raw deleted).  *)
(* worker  one queue: uploads, and the resend that lists kept files.       *)
(*                                                                         *)
(* The game can stop at any moment, which loses every thread and claim but *)
(* not the disk. The next start sweeps raw and .part files on io, and the  *)
(* offers the player accepts from are fetched only after that sweep, so a  *)
(* sweep never deletes a new run's capture.                                *)
(***************************************************************************)
EXTENDS Naturals, Sequences, FiniteSets

CONSTANT Runs

VARIABLES
    phase,      \* each run: "idle", "accepted", "recording", "finished", "discarded"
    disk,       \* each run: the subset of {"raw", "part", "gz"} on disk
    io,         \* the io thread's queue of [op, run]
    worker,     \* the worker's queue of [op, run]
    claimed,    \* runs whose .gz an upload has claimed, so a resend leaves it alone
    swept,      \* whether this start's sweep has run
    alive,      \* whether the game is running
    sent,       \* runs the backend kept
    sentHere    \* runs the backend kept since this start, which the client still remembers

vars == <<phase, disk, io, worker, claimed, swept, alive, sent, sentHere>>

Op(o, r) == [op |-> o, run |-> r]

Init ==
    /\ phase = [r \in Runs |-> "idle"]
    /\ disk = [r \in Runs |-> {}]
    /\ io = <<Op("sweep", 0)>>
    /\ worker = <<>>
    /\ claimed = {}
    /\ swept = FALSE
    /\ alive = TRUE
    /\ sent = {}
    /\ sentHere = {}

Active(r) == phase[r] \in {"accepted", "recording"}

\* One run at a time, and only from offers fetched after the sweep.
Accept(r) ==
    /\ alive /\ swept
    /\ phase[r] = "idle"
    /\ \A s \in Runs : ~Active(s)
    /\ phase' = [phase EXCEPT ![r] = "accepted"]
    /\ UNCHANGED <<disk, io, worker, claimed, swept, alive, sent, sentHere>>

\* The first key press starts the recorder, whose writer creates the raw file.
Start(r) ==
    /\ alive /\ phase[r] = "accepted"
    /\ phase' = [phase EXCEPT ![r] = "recording"]
    /\ disk' = [disk EXCEPT ![r] = @ \cup {"raw"}]
    /\ UNCHANGED <<io, worker, claimed, swept, alive, sent, sentHere>>

\* Recorder.stop hands the capture to io, whose callback seals it.
Finish(r) ==
    /\ alive /\ phase[r] = "recording"
    /\ phase' = [phase EXCEPT ![r] = "finished"]
    /\ io' = Append(io, Op("seal", r))
    /\ UNCHANGED <<disk, worker, claimed, swept, alive, sent, sentHere>>

\* Cancel, time out, disconnect or quit before the run finished.
Discard(r) ==
    /\ alive /\ Active(r)
    /\ phase' = [phase EXCEPT ![r] = "discarded"]
    /\ io' = IF phase[r] = "recording" THEN Append(io, Op("delete", r)) ELSE io
    /\ UNCHANGED <<disk, worker, claimed, swept, alive, sent, sentHere>>

\* A second /dsmod submit while one is queued adds nothing the first does not do, so the model leaves it out.
Resend ==
    /\ alive /\ ~\E i \in DOMAIN worker : worker[i].op = "resend"
    /\ worker' = Append(worker, Op("resend", 0))
    /\ UNCHANGED <<phase, disk, io, claimed, swept, alive, sent, sentHere>>

IoStep ==
    /\ alive /\ io /= <<>>
    /\ LET t == Head(io) IN
        CASE t.op = "sweep" ->
                /\ disk' = [r \in Runs |-> disk[r] \ {"raw", "part"}]
                /\ swept' = TRUE
                /\ io' = Tail(io)
                /\ UNCHANGED <<worker, claimed>>
          [] t.op = "delete" ->
                /\ disk' = [disk EXCEPT ![t.run] = @ \ {"raw"}]
                /\ io' = Tail(io)
                /\ UNCHANGED <<worker, claimed, swept>>
          \* The redactor writes the .part beside the raw file.
          [] t.op = "seal" /\ "part" \notin disk[t.run] ->
                /\ disk' = [disk EXCEPT ![t.run] = @ \cup {"part"}]
                /\ UNCHANGED <<io, worker, claimed, swept>>
          \* Then it claims the .gz, renames the .part to it, queues its upload, and deletes the raw file.
          [] t.op = "seal" ->
                /\ claimed' = claimed \cup {t.run}
                /\ disk' = [disk EXCEPT ![t.run] = (@ \ {"part"}) \cup {"gz"}]
                /\ worker' = Append(worker, Op("upload", t.run))
                /\ io' = <<Op("delete", t.run)>> \o Tail(io)
                /\ UNCHANGED swept
    /\ UNCHANGED <<phase, alive, sent, sentHere>>

\* A capture its writer did not finish is deleted instead of sealed.
IoBroken ==
    /\ alive /\ io /= <<>>
    /\ Head(io).op = "seal" /\ "part" \notin disk[Head(io).run]
    /\ disk' = [disk EXCEPT ![Head(io).run] = @ \ {"raw"}]
    /\ io' = Tail(io)
    /\ UNCHANGED <<phase, worker, claimed, swept, alive, sent, sentHere>>

\* The resend claims each kept .gz that no upload holds, and queues it.
WorkerResend ==
    /\ alive /\ worker /= <<>> /\ Head(worker).op = "resend"
    /\ LET kept == {r \in Runs : "gz" \in disk[r] /\ r \notin claimed}
           order == CHOOSE s \in [1..Cardinality(kept) -> kept] : \A i, j \in DOMAIN s : i /= j => s[i] /= s[j]
       IN /\ claimed' = claimed \cup kept
          /\ worker' = Tail(worker) \o [i \in DOMAIN order |-> Op("upload", order[i])]
    /\ UNCHANGED <<phase, disk, io, swept, alive, sent, sentHere>>

\* The backend answers. A failure releases the claim and keeps the .gz for a resend.
\* A file the backend kept or refused stays on disk, still claimed, until WorkerDrop deletes it.
\* The delete is its own step so a throw after Upload.SENT can leave the claim held.
WorkerUpload(outcome) ==
    /\ alive /\ worker /= <<>> /\ Head(worker).op = "upload"
    /\ LET r == Head(worker).run IN
        \* The backend keeps one run per player and request, so a run it already kept is refused.
        /\ outcome = "sent" => r \notin sent
        /\ sent' = IF outcome = "sent" THEN sent \cup {r} ELSE sent
        /\ sentHere' = IF outcome = "sent" THEN sentHere \cup {r} ELSE sentHere
        /\ IF outcome = "failed"
           THEN /\ claimed' = claimed \ {r}
                /\ worker' = Tail(worker)
           ELSE /\ worker' = [worker EXCEPT ![1] = Op("drop", r)]
                /\ UNCHANGED claimed
    /\ UNCHANGED <<phase, disk, io, swept, alive>>

\* The client deletes the .gz and only then releases the claim.
\* A delete that throws is not this step: the claim stays held, which is [SentStaysClaimed].
WorkerDrop ==
    /\ alive /\ worker /= <<>> /\ Head(worker).op = "drop"
    /\ LET r == Head(worker).run IN
        /\ disk' = [disk EXCEPT ![r] = @ \ {"gz"}]
        /\ claimed' = claimed \ {r}
    /\ worker' = Tail(worker)
    /\ UNCHANGED <<phase, io, swept, alive, sent, sentHere>>

\* The game stops: threads, queues and claims are gone, and the disk stays as it is. A run cut short is never
\* resumed.
Stop ==
    /\ alive
    /\ alive' = FALSE
    /\ phase' = [r \in Runs |-> IF Active(r) THEN "discarded" ELSE phase[r]]
    /\ io' = <<>> /\ worker' = <<>> /\ claimed' = {} /\ sentHere' = {}
    /\ UNCHANGED <<disk, swept, sent>>

Restart ==
    /\ ~alive
    /\ alive' = TRUE
    /\ swept' = FALSE
    /\ io' = <<Op("sweep", 0)>>
    /\ UNCHANGED <<phase, disk, worker, claimed, sent, sentHere>>

Next ==
    \/ \E r \in Runs : Accept(r) \/ Start(r) \/ Finish(r) \/ Discard(r)
    \/ Resend \/ IoStep \/ IoBroken \/ WorkerResend
    \/ \E o \in {"sent", "refused", "failed"} : WorkerUpload(o)
    \/ WorkerDrop
    \/ Stop \/ Restart

Spec == Init /\ [][Next]_vars

-----------------------------------------------------------------------------
TypeOK ==
    /\ phase \in [Runs -> {"idle", "accepted", "recording", "finished", "discarded"}]
    /\ disk \in [Runs -> SUBSET {"raw", "part", "gz"}]
    /\ claimed \subseteq Runs /\ sent \subseteq Runs /\ sentHere \subseteq sent

Queued(q, o, r) == \E i \in DOMAIN q : q[i].op = o /\ q[i].run = r

\* An upload only ever reads a whole, redacted file that is there.
UploadsOnlyGz == \A r \in Runs : alive /\ Queued(worker, "upload", r) => "gz" \in disk[r] /\ r \in claimed

\* No run is uploaded twice at once, so no upload finds its file deleted by the other.
OneUploadEach == \A r \in Runs : Cardinality({i \in DOMAIN worker : worker[i].op = "upload" /\ worker[i].run = r}) <= 1

\* What the player cancelled, or what timed out or was cut short, never reaches the backend.
DiscardedNeverSent == \A r \in Runs : phase[r] = "discarded" => r \notin sent

\* A raw capture or a half redaction is only on disk while something will finish or delete it.
NoLeftovers == \A r \in Runs :
    ({"raw", "part"} \cap disk[r] /= {}) =>
        \/ ~alive \/ ~swept
        \/ phase[r] = "recording"
        \/ Queued(io, "seal", r) \/ Queued(io, "delete", r)

\* The sweep never takes a capture a run is writing.
SweepSparesRuns == \A r \in Runs : alive /\ Queued(io, "sweep", 0) => ~Active(r)

\* A file the backend kept stays claimed while this game is running and the file is still on disk.
\* Within one start, a run the backend kept is never offered for a resend. After a crash between the backend keeping
\* it and the delete, the next start resends it and the backend refuses it, which WorkerUpload holds to.
SentStaysClaimed == \A r \in Runs : alive /\ r \in sentHere /\ "gz" \in disk[r] => r \in claimed
=============================================================================
