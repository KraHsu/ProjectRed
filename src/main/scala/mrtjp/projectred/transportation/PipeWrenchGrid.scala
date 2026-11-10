package mrtjp.projectred.transportation

import codechicken.lib.raytracer.RayTracer
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.{MovingObjectPosition, Vec3}
import net.minecraft.world.World
import net.minecraftforge.oredict.OreDictionary

/** GT's 1/4, 3/4 wrench grid without adding a mandatory GregTech dependency. */
object PipeWrenchGrid {
  def isWrench(stack: ItemStack): Boolean =
    stack != null && OreDictionary
      .getOreIDs(stack)
      .exists(id => OreDictionary.getOreName(id) == "craftingToolWrench")

  private def wrapped(value: Float): Float = (value % 1f + 1f) % 1f
  private def band(value: Float): Int =
    if (value < .25f) 0 else if (value >= .75f) 2 else 1

  def cell(face: Int, x: Float, y: Float, z: Float): Int = {
    val u = if (face < 4) wrapped(x) else wrapped(z)
    val v = if (face < 2) wrapped(z) else wrapped(y)
    band(v) * 3 + band(u)
  }

  def sideForCell(face: Int, cell: Int): Int = {
    if (face < 0 || face >= 6 || cell < 0 || cell >= 9) return -1
    val u = cell % 3
    val v = cell / 3
    if (u != 1 && v != 1) face ^ 1
    else if (u != 1) (if (face < 4) 4 else 2) + (if (u == 2) 1 else 0)
    else if (v != 1) (if (face < 2) 2 else 0) + (if (v == 2) 1 else 0)
    else face
  }

  def side(face: Int, x: Float, y: Float, z: Float): Int =
    sideForCell(face, cell(face, x, y, z))

  def hitCell(hit: MovingObjectPosition): Int =
    if (hit.hitVec == null) 4
    else
      cell(
        hit.sideHit,
        (hit.hitVec.xCoord - hit.blockX).toFloat,
        (hit.hitVec.yCoord - hit.blockY).toFloat,
        (hit.hitVec.zCoord - hit.blockZ).toFloat
      )

  def hitSide(hit: MovingObjectPosition): Int =
    sideForCell(hit.sideHit, hitCell(hit))

  /** Server FMP retraces the player's eye ray instead of trusting packet hit
    * coordinates. Only that wrench ray receives the larger selection box;
    * ordinary interaction and physical collision boxes remain unchanged.
    */
  def isWrenchRay(world: World, start: Vec3): Boolean = {
    if (world.isRemote) return TransportationProxy.clientWrenchSelection
    var i = 0
    while (i < world.playerEntities.size()) {
      val player = world.playerEntities.get(i).asInstanceOf[EntityPlayer]
      if (isWrench(player.getHeldItem)) {
        val head = RayTracer.getStartVec(player)
        if (head.squareDistanceTo(start) < 1e-8) return true
      }
      i += 1
    }
    false
  }

  // Shared, cached world-axis geometry: the visible cells and click mapping
  // use exactly the same quarter boundaries on every face.
  private val cuts = Array(0d, .25d, .75d, 1d)
  private def point(face: Int, u: Double, v: Double): Array[Double] =
    face match {
      case 0 => Array(u, -.002d, v)
      case 1 => Array(u, 1.002d, v)
      case 2 => Array(u, v, -.002d)
      case 3 => Array(u, v, 1.002d)
      case 4 => Array(-.002d, v, u)
      case 5 => Array(1.002d, v, u)
    }

  val gridLines: Array[Array[Double]] = Array.tabulate(6) { face =>
    val lines = Seq(
      (0d, 0d, 1d, 0d),
      (1d, 0d, 1d, 1d),
      (1d, 1d, 0d, 1d),
      (0d, 1d, 0d, 0d),
      (.25d, 0d, .25d, 1d),
      (.75d, 0d, .75d, 1d),
      (0d, .25d, 1d, .25d),
      (0d, .75d, 1d, .75d)
    )
    lines.flatMap { case (u, v, u2, v2) =>
      point(face, u, v) ++ point(face, u2, v2)
    }.toArray
  }
  val cellOutlines: Array[Array[Array[Double]]] = Array.tabulate(6, 9) {
    (face, cell) =>
      val u = cell % 3; val v = cell / 3
      Seq(
        (cuts(u), cuts(v)),
        (cuts(u + 1), cuts(v)),
        (cuts(u + 1), cuts(v + 1)),
        (cuts(u), cuts(v + 1))
      ).flatMap { case (a, b) => point(face, a, b) }.toArray
  }
  val connectionMarks: Array[Array[Array[Double]]] = Array.tabulate(6, 9) {
    (face, cell) =>
      val u = cell % 3; val v = cell / 3
      val a = cuts(u) + .035d; val b = cuts(u + 1) - .035d
      val c = cuts(v) + .035d; val d = cuts(v + 1) - .035d
      point(face, a, c) ++ point(face, b, d) ++ point(face, a, d) ++ point(
        face,
        b,
        c
      )
  }
}
