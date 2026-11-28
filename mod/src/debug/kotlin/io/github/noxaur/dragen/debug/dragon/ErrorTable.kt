package io.github.noxaur.dragen.debug.dragon

data class Cell(val ahead: Int, val samples: Int, val median: Double, val body: Double, val head: Double)

/** @keep Each cell is what `Forecast` got wrong in `protector-dragon`, over every tick of that basis and every start tick: the median distance to the dragon's true position, and the share of those forecasts whose predicted centre lay inside the body's and the head's box, grown by an arrow's 0.3, at the arrival tick or the two ticks either side, because an arrow takes about two ticks to cross a box, counted as (hits + 1) / (samples + 2). `AimStudyTest` builds it again from the fixture and fails with the new text when the table is stale. */
object ErrorTable {
    val AHEADS = intArrayOf(1, 3, 5, 10, 15, 20, 30, 40, 60)

    val cells: Map<Basis, List<Cell>> = mapOf(
        Basis.FLYING to listOf(Cell(1, 1195, 0.02, 1.0, 0.97), Cell(3, 1195, 0.11, 0.99, 0.52), Cell(5, 1195, 0.29, 0.97, 0.24), Cell(10, 1195, 1.13, 0.83, 0.11), Cell(15, 1195, 2.73, 0.6, 0.03), Cell(20, 1195, 5.31, 0.31, 0.01), Cell(30, 1195, 13.16, 0.03, 0.0), Cell(40, 1195, 24.4, 0.0, 0.0), Cell(60, 1195, 49.98, 0.0, 0.0)),
        Basis.HELD to listOf(Cell(1, 368, 0.0, 1.0, 1.0), Cell(3, 360, 0.0, 1.0, 1.0), Cell(5, 352, 0.0, 1.0, 1.0), Cell(10, 332, 0.0, 1.0, 0.96), Cell(15, 312, 0.0, 1.0, 0.95), Cell(20, 292, 0.0, 1.0, 0.95), Cell(30, 252, 0.0, 1.0, 0.94), Cell(40, 212, 0.0, 1.0, 0.93), Cell(60, 136, 0.0, 0.99, 0.94)),
        Basis.RAMP to listOf(Cell(1, 3, 0.03, 0.8, 0.8), Cell(3, 9, 0.09, 0.91, 0.64), Cell(5, 15, 0.2, 0.94, 0.41), Cell(10, 30, 0.87, 0.91, 0.22), Cell(15, 45, 1.77, 0.68, 0.15), Cell(20, 60, 3.16, 0.52, 0.11), Cell(30, 90, 6.82, 0.35, 0.08), Cell(40, 120, 11.34, 0.26, 0.06), Cell(60, 176, 23.61, 0.16, 0.02)),
        Basis.RECOVERING to listOf(Cell(1, 52, 0.04, 0.98, 0.98), Cell(3, 52, 0.15, 0.98, 0.76), Cell(5, 52, 0.32, 0.98, 0.52), Cell(10, 52, 0.92, 0.83, 0.02), Cell(15, 52, 1.94, 0.74, 0.02), Cell(20, 52, 3.47, 0.44, 0.02), Cell(30, 52, 11.66, 0.02, 0.02), Cell(40, 52, 26.32, 0.02, 0.02), Cell(60, 52, 58.22, 0.02, 0.02)),
    )

    fun at(basis: Basis, ahead: Int): Cell? {
        val row = cells.getValue(basis)
        if (row.isEmpty()) return null
        val above = row.firstOrNull { it.ahead >= ahead } ?: return row.last()
        val below = row.lastOrNull { it.ahead <= ahead } ?: return above
        if (above.ahead == below.ahead) return above
        val f = (ahead - below.ahead).toDouble() / (above.ahead - below.ahead)
        return Cell(ahead, minOf(below.samples, above.samples), below.median + (above.median - below.median) * f, below.body + (above.body - below.body) * f, below.head + (above.head - below.head) * f)
    }
}
