---------------------------- MODULE SequenceFire ----------------------------
EXTENDS Naturals, Sequences

CONSTANT Gap, Horizon

VARIABLES
    screen,
    typed,
    typedAt,
    typedWhere,
    now,
    sentOff,
    fires,
    droppedLive

vars == <<screen, typed, typedAt, typedWhere, now, sentOff, fires, droppedLive>>

Short == <<1, 2>>
Long == <<1, 2, 1>>
ShortPlace == "both"
LongPlace == "world"

Init ==
    /\ screen = "world"
    /\ typed = <<>>
    /\ typedAt = 0
    /\ typedWhere = "world"
    /\ now = 0
    /\ sentOff = FALSE
    /\ fires = 0
    /\ droppedLive = FALSE

Cap(seq) == IF Len(seq) > 3 THEN Tail(seq) ELSE seq

Ends(seq, bind) ==
    Len(bind) <= Len(seq) /\ SubSeq(seq, Len(seq) - Len(bind) + 1, Len(seq)) = bind

Continues(seq) ==
    \/ /\ Len(seq) >= 1
       /\ SubSeq(seq, Len(seq), Len(seq)) = SubSeq(Long, 1, 1)
    \/ /\ Len(seq) >= 2
       /\ SubSeq(seq, Len(seq) - 1, Len(seq)) = SubSeq(Long, 1, 2)

Covers(where, at) == where = "both" \/ where = at

LiveAcross(at, stored) ==
    at /= "none" /\ (
        \/ Covers(ShortPlace, at) /\ Covers(ShortPlace, stored)
        \/ Covers(LongPlace, at) /\ Covers(LongPlace, stored))

FiresHere(seq, at, stored) ==
    \/ Ends(seq, Long) /\ Covers(LongPlace, at) /\ Covers(LongPlace, stored)
    \/ Ends(seq, Short) /\ Covers(ShortPlace, at) /\ Covers(ShortPlace, stored)

Press(k) ==
    /\ now < Horizon
    /\ screen' = screen
    /\ now' = now
    /\ droppedLive' = droppedLive
    /\ LET next == Cap(Append(typed, k))
           blocked == screen = "none"
           keep == ~blocked /\ Continues(next)
           send == ~blocked /\ ~Continues(next) /\ FiresHere(next, screen, screen)
       IN /\ typed' = IF blocked THEN <<>> ELSE IF keep THEN next ELSE <<>>
          /\ typedAt' = IF blocked THEN typedAt ELSE now
          /\ typedWhere' = IF keep THEN screen ELSE typedWhere
          /\ fires' = IF send THEN fires + 1 ELSE fires
          /\ sentOff' = IF send /\ screen = "none" THEN TRUE ELSE sentOff

To(place) ==
    /\ screen' = place
    /\ UNCHANGED <<typed, typedAt, typedWhere, now, sentOff, fires, droppedLive>>

Tick ==
    /\ now < Horizon
    /\ now' = now + 1
    /\ screen' = screen
    /\ typedAt' = typedAt
    /\ typedWhere' = typedWhere
    /\ LET blocked == ~LiveAcross(screen, typedWhere)
           due == ~blocked /\ typed /= <<>> /\ now' - typedAt >= Gap
           send == due /\ FiresHere(typed, screen, typedWhere)
           wrong == typed /= <<>> /\ blocked /\ LiveAcross(screen, typedWhere)
       IN /\ typed' = IF typed /= <<>> /\ (blocked \/ due) THEN <<>> ELSE typed
          /\ fires' = IF send THEN fires + 1 ELSE fires
          /\ sentOff' = IF send /\ screen = "none" THEN TRUE ELSE sentOff
          /\ droppedLive' = (droppedLive \/ wrong)

Done == now >= Horizon /\ UNCHANGED vars

Next ==
    \/ Press(1) \/ Press(2)
    \/ To("world") \/ To("menu") \/ To("none")
    \/ Tick
    \/ Done

NoSendOffScreen == ~sentOff

SequenceBounded == Len(typed) <= 3

LiveSequenceKept == ~droppedLive

Spec == Init /\ [][Next]_vars
=============================================================================
