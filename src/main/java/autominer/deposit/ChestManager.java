package autominer.deposit;

import autominer.AutoMiner;
import autominer.config.ConfigManager;
import autominer.config.ModConfig;
import autominer.selector.BlockSelector;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Wybor i walidacja skrzynki docelowej (pozycja zapisywana razem ze swiatem i wymiarem). */
public final class ChestManager {
    public enum State { NOT_SET, OTHER_WORLD, MISSING, OUT_OF_RANGE, READY }

    private boolean picking;

    public boolean isPicking() {
        return picking;
    }

    public void startPicking() {
        picking = true;
        AutoMiner.notifications.info("LOOK AT A CHEST OR BARREL AND PRESS F5");
    }

    public void cancelPicking() {
        picking = false;
    }

    public static boolean isContainerBlock(BlockState st) {
        return st.getBlock() instanceof ChestBlock || st.getBlock() instanceof BarrelBlock;
    }

    public static String worldKey(MinecraftClient c) {
        if (c.getCurrentServerEntry() != null) {
            return "server:" + c.getCurrentServerEntry().address;
        }
        String level = "world";
        if (c.getServer() != null) {
            level = c.getServer().getSaveProperties().getLevelName();
        }
        return "singleplayer:" + level;
    }

    public static String dimension(MinecraftClient c) {
        return c.world == null ? "" : c.world.getRegistryKey().getValue().toString();
    }

    public void pickFromCrosshair(MinecraftClient c) {
        picking = false;
        BlockHitResult hit = BlockSelector.targetedBlock(c);
        if (hit == null) {
            AutoMiner.notifications.warn("NO BLOCK SELECTED");
            return;
        }
        BlockPos pos = hit.getBlockPos();
        if (!isContainerBlock(c.world.getBlockState(pos))) {
            AutoMiner.notifications.error("NOT A CHEST OR BARREL");
            return;
        }
        ModConfig cfg = ConfigManager.get();
        boolean exists = cfg.chests.stream().anyMatch(e -> e.worldKey.equals(worldKey(c)) && e.dimension.equals(dimension(c)) && e.x == pos.getX() && e.y == pos.getY() && e.z == pos.getZ());
        if (exists) { AutoMiner.notifications.warn("CHEST ALREADY ADDED"); return; }
        cfg.chests.add(new ModConfig.ChestEntry(worldKey(c), dimension(c), pos.getX(), pos.getY(), pos.getZ()));
        cfg.chestSet = true;
        ConfigManager.markDirty();
        AutoMiner.notifications.success("DEPOSIT CHEST ADDED (" + cfg.chests.size() + ")");
    }

    public void clear() {
        ModConfig cfg = ConfigManager.get();
        cfg.chests.clear();
        cfg.chestSet = false;
        ConfigManager.markDirty();
    }

    public int size() { return ConfigManager.get().chests.size(); }

    public ModConfig.ChestEntry entry(int index) {
        var list = ConfigManager.get().chests;
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    public BlockPos position() { return position(0); }

    public BlockPos position(int index) {
        ModConfig.ChestEntry e = entry(index);
        return e == null ? null : new BlockPos(e.x, e.y, e.z);
    }

    public BlockPos selectedPosInCurrentWorld(MinecraftClient c) { return selectedPosInCurrentWorld(c, 0); }

    public BlockPos selectedPosInCurrentWorld(MinecraftClient c, int index) {
        ModConfig.ChestEntry e = entry(index);
        if (e == null || c.world == null) return null;
        if (!e.worldKey.equals(worldKey(c)) || !e.dimension.equals(dimension(c))) return null;
        return position(index);
    }

    public State state(MinecraftClient c) { return state(c, 0); }

    public State state(MinecraftClient c, int index) {
        ModConfig.ChestEntry e = entry(index);
        if (e == null) return State.NOT_SET;
        if (c.world == null || c.player == null) return State.OTHER_WORLD;
        BlockPos pos = selectedPosInCurrentWorld(c, index);
        if (pos == null) return State.OTHER_WORLD;
        if (!isContainerBlock(c.world.getBlockState(pos))) return State.MISSING;
        double reach = c.player.getBlockInteractionRange() - 0.5;
        if (c.player.getEyePos().distanceTo(Vec3d.ofCenter(pos)) > reach) return State.OUT_OF_RANGE;
        return State.READY;
    }

    public int nextReadyIndex(MinecraftClient c, int startInclusive) {
        for (int i = Math.max(0, startInclusive); i < size(); i++) {
            if (state(c, i) == State.READY) return i;
        }
        return -1;
    }

    /** Czy trafiony blok to zapisana skrzynka (lub druga polowa duzej skrzyni). */
    public boolean matches(MinecraftClient c, BlockPos hit) { return matches(c, hit, 0); }

    public boolean matches(MinecraftClient c, BlockPos hit, int index) {
        BlockPos pos = position(index);
        if (pos == null || c.world == null) return false;
        if (hit.equals(pos)) return true;
        boolean adjacent = Math.abs(hit.getX() - pos.getX()) + Math.abs(hit.getY() - pos.getY())
                + Math.abs(hit.getZ() - pos.getZ()) == 1;
        return adjacent && c.world.getBlockState(hit).getBlock() instanceof ChestBlock
                && c.world.getBlockState(pos).getBlock() instanceof ChestBlock;
    }
}
