package mrtjp.projectred.integration

import mrtjp.projectred.transportation.PipeWrenchGrid
import org.junit.Assert._
import org.junit.Test

class PipeWrenchGridTest {
  @Test def nineCellsExposeEveryDirectionOnEveryFace(): Unit = {
    for (face <- 0 until 6) {
      val sides = (0 until 9).map(PipeWrenchGrid.sideForCell(face, _))
      assertEquals((0 until 6).toSet, sides.toSet)
      assertEquals(face, sides(4))
      for (corner <- Seq(0, 2, 6, 8)) assertEquals(face ^ 1, sides(corner))
    }
  }

  @Test def exactQuarterBoundariesUseTheSameCellsAsTheGrid(): Unit = {
    assertEquals(4, PipeWrenchGrid.side(2, .249f, .5f, 0f))
    assertEquals(2, PipeWrenchGrid.side(2, .25f, .5f, 0f))
    assertEquals(2, PipeWrenchGrid.side(2, .749f, .5f, 0f))
    assertEquals(5, PipeWrenchGrid.side(2, .75f, .5f, 0f))
    assertEquals(3, PipeWrenchGrid.side(2, .1f, .1f, 0f))
  }

  @Test def cachedGeometryAndCellSelectionAgreeForAll54Cells(): Unit = {
    val mids = Array(.125f, .5f, .875f)
    for (face <- 0 until 6; cell <- 0 until 9) {
      val u = mids(cell % 3); val v = mids(cell / 3)
      val xyz =
        if (face < 2) (u, .5f, v)
        else if (face < 4) (u, v, .5f)
        else (.5f, v, u)
      assertEquals(cell, PipeWrenchGrid.cell(face, xyz._1, xyz._2, xyz._3))
      assertEquals(12, PipeWrenchGrid.cellOutlines(face)(cell).length)
      assertEquals(12, PipeWrenchGrid.connectionMarks(face)(cell).length)
      assertEquals(48, PipeWrenchGrid.gridLines(face).length)
    }
  }
}
