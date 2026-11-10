package mrtjp.projectred.transportation

import cpw.mods.fml.relauncher.{Side, SideOnly}
import net.minecraft.client.Minecraft
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.MovingObjectPosition
import org.lwjgl.opengl.{GL11, GL20}

@SideOnly(Side.CLIENT)
object PipeWrenchRenderer {
  def isWrenchHeld: Boolean = {
    val player = Minecraft.getMinecraft.thePlayer
    player != null && PipeWrenchGrid.isWrench(player.getHeldItem)
  }

  private def vertices(points: Array[Double]) {
    var i = 0
    while (i < points.length) {
      GL11.glVertex3d(points(i), points(i + 1), points(i + 2))
      i += 3
    }
  }

  /** Separated from world/camera access for offscreen GL state/geometry tests.
    */
  def drawFace(face: Int, connections: Int, hoveredCell: Int) {
    if (face < 0 || face >= 6) return
    val program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
    try {
      GL20.glUseProgram(0)
      GL11.glDisable(GL11.GL_TEXTURE_2D)
      GL11.glDisable(GL11.GL_LIGHTING)
      GL11.glEnable(GL11.GL_BLEND)
      GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
      GL11.glDepthMask(false)
      GL11.glLineWidth(2f)
      GL11.glColor4f(.85f, .85f, .85f, .8f)
      GL11.glBegin(GL11.GL_LINES)
      vertices(PipeWrenchGrid.gridLines(face))
      GL11.glEnd()

      GL11.glColor4f(.25f, .95f, .5f, .95f)
      GL11.glBegin(GL11.GL_LINES)
      var cell = 0
      while (cell < 9) {
        if ((connections & (1 << PipeWrenchGrid.sideForCell(face, cell))) != 0)
          vertices(PipeWrenchGrid.connectionMarks(face)(cell))
        cell += 1
      }
      GL11.glEnd()

      if (hoveredCell >= 0 && hoveredCell < 9) {
        GL11.glColor4f(1f, .7f, .15f, 1f)
        GL11.glBegin(GL11.GL_LINE_LOOP)
        vertices(PipeWrenchGrid.cellOutlines(face)(hoveredCell))
        GL11.glEnd()
      }
    } finally {
      GL20.glUseProgram(program)
      GL11.glPopAttrib()
    }
  }

  def draw(
      pipe: SubcorePipePart,
      hit: MovingObjectPosition,
      player: EntityPlayer,
      frame: Float
  ) {
    val camX = player.lastTickPosX + (player.posX - player.lastTickPosX) * frame
    val camY = player.lastTickPosY + (player.posY - player.lastTickPosY) * frame
    val camZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * frame
    GL11.glPushMatrix()
    try {
      GL11.glTranslated(pipe.x - camX, pipe.y - camY, pipe.z - camZ)
      drawFace(hit.sideHit, pipe.clientConnMap, PipeWrenchGrid.hitCell(hit))
    } finally GL11.glPopMatrix()
  }
}
