import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Test-only boundary: removes unrelated GL/item setup, keeps real pipe draw methods. */
public final class FixtureClasses implements Opcodes {

    public static void main(String[] args) throws Exception {
        Path dir = Paths.get(args[1]);
        try (JarFile jar = new JarFile(args[0])) {
            String name = "mrtjp/projectred/transportation/RenderPipe$";
            byte[] input = jar.getInputStream(jar.getJarEntry(name + ".class")).readAllBytes();
            ClassNode c = new ClassNode(ASM5);
            new ClassReader(input).accept(c, 0);
            Set<String> keep = new HashSet<>(
                    Arrays.asList(
                            "<init>",
                            "<clinit>",
                            "renderPipe",
                            "renderRSWiring",
                            "renderColourWool",
                            "renderInv"));
            c.methods.removeIf(raw -> {
                MethodNode m = (MethodNode) raw;
                return !keep.contains(m.name) && !m.name.matches("(side|center)Models(RS|Wool)?(_\\$eq)?");
            });
            c.fields.removeIf(raw -> {
                FieldNode f = (FieldNode) raw;
                return f.name.equals("dummyEntityItem") || f.name.equals("customRenderItem");
            });
            for (Object raw : c.methods) {
                MethodNode m = (MethodNode) raw;
                if (!m.name.equals("<init>")) continue;
                AbstractInsnNode start = null, end = null;
                for (AbstractInsnNode i : m.instructions.toArray()) if (i instanceof FieldInsnNode) {
                    FieldInsnNode f = (FieldInsnNode) i;
                    if (f.name.equals("MODULE$") && f.getOpcode() == PUTSTATIC) start = i.getNext();
                    if (f.name.equals("sideModels") && f.getOpcode() == PUTFIELD) {
                        end = i.getPrevious().getPrevious();
                        break;
                    }
                }
                if (start == null || end == null) throw new IllegalStateException("Unexpected initializer");
                for (AbstractInsnNode i = start; i != end;) {
                    AbstractInsnNode next = i.getNext();
                    m.instructions.remove(i);
                    i = next;
                }
                m.localVariables = null;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            c.accept(writer);
            Path file = dir.resolve(name + ".class");
            Files.createDirectories(file.getParent());
            Files.write(file, writer.toByteArray());
            // Count actual model constructions without changing production classes.
            name = "mrtjp/projectred/transportation/PipeModelGenerator";
            input = jar.getInputStream(jar.getJarEntry(name + ".class")).readAllBytes();
            c = new ClassNode(ASM5);
            new ClassReader(input).accept(c, 0);
            c.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC, "fixtureConstructions", "J", null, null));
            for (Object raw : c.methods) {
                MethodNode m = (MethodNode) raw;
                if (!m.name.equals("<init>")) continue;
                for (AbstractInsnNode i : m.instructions.toArray()) if (i.getOpcode() == RETURN) {
                    InsnList inc = new InsnList();
                    inc.add(new FieldInsnNode(GETSTATIC, name, "fixtureConstructions", "J"));
                    inc.add(new InsnNode(LCONST_1));
                    inc.add(new InsnNode(LADD));
                    inc.add(new FieldInsnNode(PUTSTATIC, name, "fixtureConstructions", "J"));
                    m.instructions.insertBefore(i, inc);
                }
            }
            writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            c.accept(writer);
            file = dir.resolve(name + ".class");
            Files.write(file, writer.toByteArray());
        }
    }
}
