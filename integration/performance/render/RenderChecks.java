import java.lang.management.*;
import java.util.*;

import net.minecraft.client.renderer.Tessellator;

import codechicken.lib.render.*;
import codechicken.lib.vec.*;
import mrtjp.projectred.transportation.*;

/** Real ProjectRed draw code + CCL pipeline, captured at the Minecraft Tessellator boundary. */
public final class RenderChecks {

    static long hash = 0xcbf29ce484222325L;

    static void mix(long value) {
        hash = (hash ^ value) * 0x100000001b3L;
    }

    static void render(RenderPipe$ renderer, SubcorePipePart pipe, Vector3 pos) {
        CCRenderState state = CCRenderState.instance();
        state.resetInstance();
        state.setDynamicInstance();
        renderer.renderPipe(pipe, pos);
    }

    static long geometry(RenderPipe$ renderer) {
        long previous = hash;
        hash = 0xcbf29ce484222325L;
        CCModel[][] groups = { renderer.sideModels(), renderer.centerModels(), renderer.sideModelsRS(),
                renderer.centerModelsRS(), renderer.sideModelsWool(), renderer.centerModelsWool() };
        for (CCModel[] models : groups) for (CCModel model : models) {
            for (Vertex5 v : model.verts) {
                mix(Double.doubleToLongBits(v.vec.x));
                mix(Double.doubleToLongBits(v.vec.y));
                mix(Double.doubleToLongBits(v.vec.z));
                mix(Double.doubleToLongBits(v.uv.u));
                mix(Double.doubleToLongBits(v.uv.v));
            }
            for (Vector3 n : model.normals()) {
                mix(Double.doubleToLongBits(n.x));
                mix(Double.doubleToLongBits(n.y));
                mix(Double.doubleToLongBits(n.z));
            }
            for (Object attribute : model.attributes)
                if (attribute instanceof int[]) for (int i : (int[]) attribute) mix(i);
        }
        long answer = hash;
        hash = previous;
        return answer;
    }

    public static void main(String[] args) throws Exception {
        RenderPipe$ renderer = RenderPipe$.MODULE$;
        SubcorePipePart pipe = new SubcorePipePart();
        render(renderer, pipe, new Vector3()); // Original reaches its steady-state overlay scale.
        long expectedGeometry = geometry(renderer), constructions = PipeModelGenerator.fixtureConstructions;
        Tessellator t = Tessellator.field_78398_a;
        t.capture = true;
        long fingerprint = 0xcbf29ce484222325L;
        int cases = 0;
        double[][] positions = { { 0, 0, 0 }, { 12, 64, -27 }, { -2048, 255, 4096 }, { 0.25, -0.5, 1.75 } };
        for (int pass = 0; pass < 3; pass++) for (int mask = 0; mask < 64; mask++) for (double[] position : positions) {
            pipe.mask = mask;
            pipe.texture = pass;
            render(renderer, pipe, new Vector3(position[0], position[1], position[2]));
            fingerprint = Long.rotateLeft(fingerprint, 7) ^ t.takeHash();
            CCRenderState state = CCRenderState.instance();
            state.resetInstance();
            state.setDynamicInstance();
            renderer.renderRSWiring(pipe, new Vector3(position[0], position[1], position[2]), (byte) (mask * 4));
            fingerprint = Long.rotateLeft(fingerprint, 7) ^ t.takeHash();
            state.resetInstance();
            state.setDynamicInstance();
            renderer.renderColourWool(pipe, new Vector3(position[0], position[1], position[2]), (byte) (mask % 16));
            fingerprint = Long.rotateLeft(fingerprint, 7) ^ t.takeHash();
            cases += 3;
        }
        for (int i = 0; i < 64; i++) {
            CCRenderState state = CCRenderState.instance();
            state.resetInstance();
            state.setDynamicInstance();
            renderer.renderInv(
                    scala.Predef$.MODULE$.wrapRefArray(
                            new CCRenderState.IVertexOperation[] { new Translation(i, -i, 0),
                                    new codechicken.lib.render.uv.IconTransformation(pipe.getIcon(i % 6)) }));
            fingerprint = Long.rotateLeft(fingerprint, 7) ^ t.takeHash();
            cases++;
        }
        if (geometry(renderer) != expectedGeometry) throw new AssertionError("Cached geometry mutated");
        long constructionsInChecks = PipeModelGenerator.fixtureConstructions - constructions;
        if (args[0].equals("patched") && constructionsInChecks != 0)
            throw new AssertionError("Model regeneration remains: " + constructionsInChecks);
        t.capture = false;
        for (int i = 0; i < 4000; i++) {
            pipe.mask = i & 63;
            render(renderer, pipe, new Vector3(i & 15, 64, i & 31));
        }
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (bean.isThreadAllocatedMemorySupported() && !bean.isThreadAllocatedMemoryEnabled())
            bean.setThreadAllocatedMemoryEnabled(true);
        int loops = 5000, rounds = 7;
        long allocated = 0;
        double[] nanos = new double[rounds];
        long startConstructions = PipeModelGenerator.fixtureConstructions;
        for (int round = 0; round < rounds; round++) {
            long beforeAlloc = bean.getThreadAllocatedBytes(Thread.currentThread().getId()), start = System.nanoTime();
            for (int i = 0; i < loops; i++) {
                pipe.mask = i & 63;
                render(renderer, pipe, new Vector3(i & 15, 64, i & 31));
            }
            nanos[round] = (System.nanoTime() - start) / (double) loops;
            allocated += bean.getThreadAllocatedBytes(Thread.currentThread().getId()) - beforeAlloc;
        }
        Arrays.sort(nanos);
        System.out.printf(
                Locale.ROOT,
                "{\"variant\":\"%s\",\"cases\":%d,\"vertex_fingerprint\":\"%016x\",\"geometry_fingerprint\":\"%016x\",\"check_model_constructions\":%d,\"benchmark_model_constructions\":%d,\"median_ns_per_pipe\":%.2f,\"allocated_bytes_per_pipe\":%.2f,\"measured_pipe_calls\":%d,\"boundary\":\"real ProjectRed and CCL; test world/icons and captured Tessellator; no GPU or Angelica\"}%n",
                args[0],
                cases,
                fingerprint,
                expectedGeometry,
                constructionsInChecks,
                PipeModelGenerator.fixtureConstructions - startConstructions,
                nanos[rounds / 2],
                allocated / (double) (loops * rounds),
                loops * rounds);
    }
}
