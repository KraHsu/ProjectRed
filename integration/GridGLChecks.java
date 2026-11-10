package runtimechecks;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import mrtjp.projectred.transportation.PipeWrenchRenderer$;

/** Actual client renderer in an isolated compatibility GL context, without a game world. */
public final class GridGLChecks {
    static void require(boolean v,String message){if(!v)throw new AssertionError(message);}
    static int shader(int type,String source){int id=GL20.glCreateShader(type);GL20.glShaderSource(id,source);GL20.glCompileShader(id);require(GL20.glGetShaderi(id,GL20.GL_COMPILE_STATUS)!=0,GL20.glGetShaderInfoLog(id,2048));return id;}
    public static void main(String[] args)throws Exception {
        int width=768,height=512;
        Display.setDisplayMode(new DisplayMode(width,height));Display.setTitle("Isolated pipe wrench grid check");
        Display.create(new PixelFormat().withDepthBits(24));
        try {
            int program=GL20.glCreateProgram();
            GL20.glAttachShader(program,shader(GL20.GL_VERTEX_SHADER,"#version 120\nvoid main(){gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex;gl_FrontColor=gl_Color;}"));
            GL20.glAttachShader(program,shader(GL20.GL_FRAGMENT_SHADER,"#version 120\nvoid main(){gl_FragColor=gl_Color;}"));
            GL20.glLinkProgram(program);require(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)!=0,"Shader link failed");
            GL11.glClearColor(0f,0f,0f,1f);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
            int stateChecks=0;
            for(int face=0;face<6;face++) {
                GL11.glViewport((face%3)*256,(1-face/3)*256,256,256);
                GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(-.08,1.08,-.08,1.08,-3,3);
                GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();
                if(face<2)GL11.glRotated(-90,1,0,0);else if(face>=4)GL11.glRotated(90,0,1,0);
                GL20.glUseProgram(program);GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_ONE,GL11.GL_ZERO);
                GL11.glDepthMask(true);GL11.glLineWidth(3f);GL11.glColor4f(.2f,.3f,.4f,.5f);
                FloatBuffer before=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,before);
                PipeWrenchRenderer$.MODULE$.drawFace(face,(1<<face)|(1<<1)|(1<<5),face);
                require(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==program,"Shader program leaked");stateChecks++;
                require(GL11.glIsEnabled(GL11.GL_TEXTURE_2D)&&GL11.glIsEnabled(GL11.GL_LIGHTING)&&!GL11.glIsEnabled(GL11.GL_BLEND),"Enable state leaked");stateChecks++;
                require(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)&&GL11.glGetFloat(GL11.GL_LINE_WIDTH)==3f,"Depth/line state leaked");stateChecks++;
                require(GL11.glGetInteger(GL11.GL_BLEND_SRC)==GL11.GL_ONE&&GL11.glGetInteger(GL11.GL_BLEND_DST)==GL11.GL_ZERO,"Blend function leaked");stateChecks++;
                FloatBuffer after=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,after);
                for(int i=0;i<16;i++)require(before.get(i)==after.get(i),"Model matrix leaked");stateChecks+=16;
                FloatBuffer color=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_CURRENT_COLOR,color);
                require(Math.abs(color.get(0)-.2f)<1e-5&&Math.abs(color.get(3)-.5f)<1e-5,"Current colour leaked");stateChecks++;
                require(GL11.glGetError()==GL11.GL_NO_ERROR,"GL error after renderer");stateChecks++;
            }
            GL11.glFinish();ByteBuffer pixels=BufferUtils.createByteBuffer(width*height*4);
            GL11.glReadPixels(0,0,width,height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
            BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);int nonblack=0;
            for(int y=0;y<height;y++)for(int x=0;x<width;x++){
                int i=(y*width+x)*4,r=pixels.get(i)&255,g=pixels.get(i+1)&255,b=pixels.get(i+2)&255;
                if(r+g+b>20)nonblack++;image.setRGB(x,height-1-y,(r<<16)|(g<<8)|b);
            }
            require(nonblack>10000,"Grid did not render");ImageIO.write(image,"png",new File(args[0]));
            System.out.println("{\"passed\":true,\"faces_rendered\":6,\"state_checks\":"+stateChecks+",\"nonblack_pixels\":"+nonblack+",\"gl_renderer\":\""+GL11.glGetString(GL11.GL_RENDERER)+"\",\"limits\":\"Offscreen OpenGL; not a full GTNH/Angelica or Windows client test\"}");
        } finally {Display.destroy();}
    }
}
