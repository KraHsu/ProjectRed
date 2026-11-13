package runtimechecks;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Runs only inside the loopback-only disposable GTNH harness. */
public class NativeContribution {

    static Class<?> c(String name) throws Exception {
        return Class.forName(name);
    }

    static Field fld(Class<?> type, String... names) throws Exception {
        for (Class<?> k = type; k != null; k = k.getSuperclass()) for (String n : names) try {
            Field f = k.getDeclaredField(n);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(type + Arrays.toString(names));
    }

    static Object get(Object o, String... names) throws Exception {
        return fld(o instanceof Class ? (Class<?>) o : o.getClass(), names).get(o instanceof Class ? null : o);
    }

    static Object call(Object o, String a, String b, Class<?>[] types, Object... args) throws Exception {
        Class<?> type = o instanceof Class ? (Class<?>) o : o.getClass();
        Method m;
        try {
            m = type.getMethod(a, types);
        } catch (NoSuchMethodException ex) {
            m = type.getMethod(b, types);
        }
        m.setAccessible(true);
        return m.invoke(o instanceof Class ? null : o, args);
    }

    static final String NS = "mrtjp.projectred.transportation.";

    static Object invoke(Object o, String n, Class<?>[] t, Object... a) throws Exception {
        Method m = (o instanceof Class ? (Class<?>) o : o.getClass()).getMethod(n, t);
        m.setAccessible(true);
        return m.invoke(o instanceof Class ? null : o, a);
    }

    static Object no(Object o, String n) throws Exception {
        return invoke(o, n, new Class<?>[] {});
    }

    static Object world, coal;

    static Object stack(int count) throws Exception {
        return c("net.minecraft.item.ItemStack").getConstructor(c("net.minecraft.item.Item"), int.class, int.class)
                .newInstance(coal, count, 0);
    }

    static Object chest(int x, int y, int z) throws Exception {
        Object registry = get(c("net.minecraft.block.Block"), "field_149771_c");
        Object block = call(registry, "getObject", "func_82594_a", new Class<?>[] { String.class }, "minecraft:chest");
        invoke(
                world,
                "func_147465_d",
                new Class<?>[] { int.class, int.class, int.class, c("net.minecraft.block.Block"), int.class,
                        int.class },
                x,
                y,
                z,
                block,
                0,
                3);
        return invoke(world, "func_147438_o", new Class<?>[] { int.class, int.class, int.class }, x, y, z);
    }

    static Object pipe(int x, int y, int z, String type, int meta) throws Exception {
        Object part = c(NS + type).newInstance();
        invoke(part, "preparePlacement", new Class<?>[] { int.class, int.class }, 0, meta);
        Object coord = c("codechicken.lib.vec.BlockCoord").getConstructor(int.class, int.class, int.class)
                .newInstance(x, y, z);
        invoke(
                c("codechicken.multipart.TileMultipart"),
                "addPart",
                new Class<?>[] { c("net.minecraft.world.World"), coord.getClass(),
                        c("codechicken.multipart.TMultiPart") },
                world,
                coord,
                part);
        return part;
    }

    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static Object module(String n) throws Exception {
        return get(c(n + "$"), "MODULE$");
    }

    static Object emptySet() throws Exception {
        return no(module("scala.collection.immutable.Set"), "empty");
    }

    static Object add(Object set, Object value) throws Exception {
        return invoke(set, "$plus", new Class<?>[] { Object.class }, value);
    }

    static Object filter(int i) throws Exception {
        Object f = c(NS + "PathFilter").newInstance();
        invoke(f, "colors_$eq", new Class<?>[] { int.class }, i);
        return f;
    }

    static int inventoryCount(Object chest) throws Exception {
        int count = 0, size = ((Number) no(chest, "func_70302_i_")).intValue();
        for (int slot = 0; slot < size; slot++) {
            Object s = invoke(chest, "func_70301_a", new Class<?>[] { int.class }, slot);
            if (s != null) count += ((Number) get(s, "field_77994_a")).intValue();
        }
        return count;
    }

    static void write(String output, Map<String, Object> result) throws Exception {
        Object gson = c("com.google.gson.Gson").newInstance();
        Files.write(
                Paths.get(output),
                ((String) invoke(gson, "toJson", new Class<?>[] { Object.class }, result)).getBytes("UTF-8"));
    }

    public static void run(String output) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            world = invoke(
                    c("net.minecraftforge.common.DimensionManager"),
                    "getWorld",
                    new Class<?>[] { int.class },
                    0);
            Object registry = get(c("net.minecraft.item.Item"), "field_150901_e");
            coal = call(registry, "getObject", "func_82594_a", new Class<?>[] { String.class }, "minecraft:coal");
            Object provider = no(world, "func_72863_F");
            for (int cx = 5; cx <= 11; cx++) for (int cz = 5; cz <= 8; cz++)
                invoke(provider, "func_73158_c", new Class<?>[] { int.class, int.class }, cx, cz);
            for (int x = 95; x <= 165; x++) for (int z = 95; z <= 101; z++)
                invoke(world, "func_147468_f", new Class<?>[] { int.class, int.class, int.class }, x, 200, z);
            Object left = chest(95, 200, 96), right = chest(100, 200, 96);
            Object[] tubes = new Object[4];
            for (int i = 0; i < 4; i++) {
                tubes[i] = pipe(96 + i, 200, 96, i % 2 == 0 ? "PressureTube" : "ResistanceTube", i % 2 == 0 ? 7 : 8);
                invoke(tubes[i], "colour_$eq", new Class<?>[] { byte.class }, (byte) 14);
            }
            for (int tick = 0; tick < 3; tick++) for (Object p : tubes) no(p, "update");
            Object payload = c(NS + "PressurePayload").getConstructor(int.class).newInstance(24501);
            invoke(payload, "setItemStack", new Class<?>[] { c("net.minecraft.item.ItemStack") }, stack(64));
            invoke(payload, "colour_$eq", new Class<?>[] { byte.class }, (byte) 14);
            invoke(tubes[0], "injectPayload", new Class<?>[] { c(NS + "PressurePayload"), int.class }, payload, 5);
            for (int tick = 0; tick < 150; tick++) for (Object p : tubes) no(p, "update");
            check(inventoryCount(left) + inventoryCount(right) == 64, "Pressure transport lost or duplicated items");
            check(inventoryCount(right) == 64, "Dyed pressure/resistance tube did not deliver");
            result.put("dyed_pressure_and_resistance_delivery_64", true);
            Set<Integer> ips = new HashSet<>();
            for (int i = 0; i < 64; i++) {
                Object part = pipe(96 + i, 200, 100, "RoutedJunctionPipePart", 1);
                no(part, "update");
                Object router = no(part, "getRouter");
                int ip = ((Number) no(router, "getIPAddress")).intValue();
                check(ips.add(ip), "Duplicate router address");
                Object found = invoke(module(NS + "RouterServices"), "getRouter", new Class<?>[] { int.class }, ip);
                check(found == router, "Router registry lost an entry");
            }
            result.put("registered_unique_native_routers", ips.size());
            Object versions = get(
                    module(NS + "Router"),
                    "LegacyLinkStateID",
                    "mrtjp$projectred$transportation$Router$$LegacyLinkStateID");
            for (int i = 0; i < Array.getLength(versions); i++) {
                Object found = invoke(module(NS + "RouterServices"), "getRouter", new Class<?>[] { int.class }, i);
                if (found == null && i > 0)
                    check(Array.getInt(versions, i) == -1, "Unused version slot was zero initialized");
            }
            result.put("unused_version_sentinels_preserved", true);
            Object set = emptySet();
            for (int i = 1; i <= 8; i++) set = add(set, filter(i));
            Object duplicate = filter(5);
            boolean contains = (Boolean) invoke(set, "contains", new Class<?>[] { Object.class }, duplicate);
            int size = ((Number) no(add(set, duplicate), "size")).intValue();
            boolean optimized = Boolean.getBoolean("pr.followups");
            check(contains == optimized && size == (optimized ? 8 : 9), "Unexpected filter-hash behavior");
            if (optimized) {
                Object path = c(NS + "Path").getConstructor(c("scala.collection.immutable.Set")).newInstance(set);
                Object first = no(set, "head");
                boolean blocked = false;
                try {
                    invoke(first, "colors_$eq", new Class<?>[] { int.class }, 99);
                } catch (InvocationTargetException e) {
                    blocked = e.getCause() instanceof IllegalStateException;
                }
                check(blocked, "Published filter could mutate in a hash set");
            }
            result.put("equal_filter_copy_found", contains);
            result.put("set_size_after_equal_copy", size);
            result.put("passed", true);
        } catch (Throwable e) {
            e.printStackTrace();
            result.put("error", e.toString());
            if (e.getCause() != null) result.put("cause", e.getCause().toString());
        }
        write(output, result);
    }

    public static void cold(String output) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            world = invoke(
                    c("net.minecraftforge.common.DimensionManager"),
                    "getWorld",
                    new Class<?>[] { int.class },
                    0);
            Object provider = no(world, "func_72863_F");
            for (int cx = 5; cx <= 7; cx++) for (int cz = 5; cz <= 7; cz++)
                invoke(provider, "func_73158_c", new Class<?>[] { int.class, int.class }, cx, cz);
            Object target = invoke(
                    world,
                    "func_147438_o",
                    new Class<?>[] { int.class, int.class, int.class },
                    100,
                    200,
                    96);
            check(inventoryCount(target) == 64, "Delivered inventory was not saved");
            for (int i = 0; i < 4; i++) {
                Object tile = invoke(
                        world,
                        "func_147438_o",
                        new Class<?>[] { int.class, int.class, int.class },
                        96 + i,
                        200,
                        96);
                Object part = invoke(tile, "partMap", new Class<?>[] { int.class }, 6);
                check(((Number) no(part, "colour")).intValue() == 14, "Wool color was not saved");
            }
            result.put("cold_inventory_and_dyed_tubes", true);
            result.put("passed", true);
        } catch (Throwable e) {
            e.printStackTrace();
            result.put("error", e.toString());
            if (e.getCause() != null) result.put("cause", e.getCause().toString());
        }
        write(output, result);
    }
}
