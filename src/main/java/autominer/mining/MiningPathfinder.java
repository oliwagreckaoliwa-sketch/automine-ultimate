package autominer.mining;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;

/**
 * Bounded 3D A* navigator for the player's walking space.
 * Nodes represent safe feet positions, not target block positions.
 */
public final class MiningPathfinder {
    private static final int HORIZONTAL_LIMIT = 20;
    private static final int VERTICAL_LIMIT = 8;
    private static final int MAX_VISITED = 1400;

    private record Node(BlockPos pos, double score) {}

    private MiningPathfinder() {}

    /** Returns a route of feet positions, excluding start and including a position near the goal. */
    public static List<BlockPos> findPath(MinecraftClient client, BlockPos goalBlock) {
        if (client.player == null || client.world == null) return List.of();
        BlockPos start = client.player.getBlockPos();
        if (nearGoal(start, goalBlock)) return List.of();

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::score));
        Map<BlockPos, Double> costs = new HashMap<>();
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        costs.put(start, 0.0);
        open.add(new Node(start, heuristic(start, goalBlock)));

        BlockPos reached = null;
        int visited = 0;
        while (!open.isEmpty() && visited++ < MAX_VISITED) {
            BlockPos current = open.poll().pos();
            if (nearGoal(current, goalBlock)) {
                reached = current;
                break;
            }
            double currentCost = costs.getOrDefault(current, Double.POSITIVE_INFINITY);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    for (int dy = -3; dy <= 1; dy++) {
                        BlockPos next = current.add(dx, dy, dz);
                        if (Math.abs(next.getX() - start.getX()) > HORIZONTAL_LIMIT
                                || Math.abs(next.getZ() - start.getZ()) > HORIZONTAL_LIMIT
                                || Math.abs(next.getY() - start.getY()) > VERTICAL_LIMIT
                                || !standable(client, next)) continue;
                        // Do not cut diagonally through a wall or a closed corner.
                        if (dx != 0 && dz != 0
                                && (!standable(client, current.add(dx, dy, 0))
                                || !standable(client, current.add(0, dy, dz)))) continue;

                        double step = (dx != 0 && dz != 0 ? 1.42 : 1.0) + Math.abs(dy) * 0.85;
                        double candidate = currentCost + step;
                        if (candidate >= costs.getOrDefault(next, Double.POSITIVE_INFINITY)) continue;
                        costs.put(next, candidate);
                        previous.put(next, current);
                        open.add(new Node(next, candidate + heuristic(next, goalBlock)));
                    }
                }
            }
        }

        if (reached == null) return List.of();
        List<BlockPos> route = new ArrayList<>();
        BlockPos cursor = reached;
        while (!cursor.equals(start)) {
            route.add(cursor);
            cursor = previous.get(cursor);
            if (cursor == null) return List.of();
        }
        Collections.reverse(route);
        return route;
    }

    private static boolean nearGoal(BlockPos feet, BlockPos target) {
        int dx = feet.getX() - target.getX();
        int dz = feet.getZ() - target.getZ();
        return dx * dx + dz * dz <= 4
                && Math.abs(feet.getY() - target.getY()) <= 2;
    }

    private static double heuristic(BlockPos from, BlockPos target) {
        double horizontal = Math.sqrt(from.getSquaredDistance(target));
        return Math.max(0.0, horizontal - 1.4) + Math.max(0, Math.abs(from.getY() - target.getY()) - 1) * 0.8;
    }

    private static boolean standable(MinecraftClient client, BlockPos feet) {
        BlockPos head = feet.up();
        BlockPos floor = feet.down();
        BlockState feetState = client.world.getBlockState(feet);
        BlockState headState = client.world.getBlockState(head);
        BlockState floorState = client.world.getBlockState(floor);
        if (!feetState.getCollisionShape(client.world, feet).isEmpty()
                || !headState.getCollisionShape(client.world, head).isEmpty()
                || floorState.getCollisionShape(client.world, floor).isEmpty()) return false;
        if (!feetState.getFluidState().isEmpty() || !headState.getFluidState().isEmpty()
                || feetState.getFluidState().isIn(FluidTags.LAVA)
                || floorState.getFluidState().isIn(FluidTags.LAVA)) return false;
        return !dangerous(feetState) && !dangerous(floorState);
    }

    private static boolean dangerous(BlockState state) {
        return state.isBurnable()
                || state.getBlock().getTranslationKey().contains("cactus")
                || state.getBlock().getTranslationKey().contains("magma")
                || state.getBlock().getTranslationKey().contains("berry_bush");
    }
}
