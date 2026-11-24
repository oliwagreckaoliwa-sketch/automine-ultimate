package autominer.gui;

import autominer.AutoMiner;
import autominer.config.ConfigManager;
import autominer.config.ModConfig;
import autominer.deposit.AutoDepositManager;
import autominer.deposit.ChestManager;
import autominer.selector.BlockInfo;
import autominer.util.Fmt;
import java.util.ArrayList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

/** Zakladka "Auto Deposit": skrzynka, status, filtry i ustawienia depozytowania. */
public final class DepositScreen extends GuiPanel {
    private static final int ROW = 20;
    private int contentH = 500;

    @Override
    public String title() {
        return "Auto Deposit";
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h, MinecraftClient c) {
        ModConfig cfg = ConfigManager.get();
        AutoDepositManager d = AutoMiner.deposit;
        ChestManager ch = AutoMiner.chests;
        int off = ui.scrollBegin("deposit", x, y, w, h, contentH);
        int cw = w - 8;
        int top = y - off;
        int cy = top + 2;
        int ix = x + 10;
        int iw = cw - 20;

        // --- zapisane skrzynki ---
        int chestRows = Math.max(1, cfg.chests.size());
        ui.card(x, cy, cw, 30 + chestRows * 18);
        ui.text("Saved chests: " + cfg.chests.size(), ix, cy + 8, Draw.TEXT);
        if (cfg.chests.isEmpty()) {
            ui.text("No chest selected", ix, cy + 19, Draw.WARN);
        } else {
            for (int i = 0; i < cfg.chests.size(); i++) {
                var e = cfg.chests.get(i);
                ChestManager.State state = ch.state(c, i);
                String status = switch (state) {
                    case READY -> "Ready";
                    case OUT_OF_RANGE -> "Out of reach";
                    case OTHER_WORLD -> "Other world";
                    case MISSING -> "Missing";
                    default -> "Unavailable";
                };
                int color = state == ChestManager.State.READY ? Draw.ON : Draw.WARN;
                int yy = cy + 20 + i * 18;
                ui.text((i + 1) + ". " + e.x + ", " + e.y + ", " + e.z + "  |  " + status, ix, yy, color);
            }
        }
        cy += 38 + chestRows * 18;

        ui.text("Free slots in last opened chest: " + (d.lastFreeSlots() < 0 ? "unknown" : d.lastFreeSlots()), ix, cy, Draw.TEXT);
        cy += 14;
        ui.text("Pending items: " + Fmt.num(ui.num("pending", d.pendingItems(c))), ix, cy, Draw.TEXT);
        cy += 14;
        ui.text("Last result: " + (d.lastResult().isEmpty() ? "-" : d.lastResult()), ix, cy, Draw.MUTED);
        cy += 20;

        int third = (cw - 16) / 3;
        ui.button("selchest", x, cy, third, 22, "ADD CHEST", Ui.Style.PRIMARY, true, () -> {
            c.setScreen(null);
            ch.startPicking();
        });
        ui.button("clrchest", x + third + 8, cy, third, 22, "CLEAR ALL", Ui.Style.GHOST, !cfg.chests.isEmpty(), ch::clear);
        ui.button("depnow", x + (third + 8) * 2, cy, cw - (third + 8) * 2, 22, "DEPOSIT NOW", Ui.Style.SUCCESS, !d.busy(), () -> {
            d.requestNow();
            c.setScreen(null);
        });
        cy += 30;

        // --- ustawienia ---
        int ry;
        int rows = 6;
        ui.card(x, cy, cw, rows * ROW + 14);
        ry = cy + 8;
        tog(ui, "depon", ix, ry, iw, "Auto Deposit enabled (F7)", cfg.depositEnabled, b -> AutoMiner.deposit.toggleEnabled());
        ry += ROW;
        int stepSec = cfg.depositIntervalSeconds < 60 ? 5 : cfg.depositIntervalSeconds < 600 ? 30 : 60;
        stepI(ui, "depint", ix, ry, iw, "Deposit interval", cfg.depositIntervalSeconds, stepSec, 5, 3600, " s", v -> cfg.depositIntervalSeconds = v);
        ry += ROW;
        tog(ui, "depfull", ix, ry, iw, "Deposit when inventory is full", cfg.depositWhenFull, b -> cfg.depositWhenFull = b);
        ry += ROW;
        stepI(ui, "depfree", ix, ry, iw, "Treat as full at free slots <=", cfg.fullFreeSlots, 1, 0, 8, "", v -> cfg.fullFreeSlots = v);
        ry += ROW;
        stepI(ui, "depclk", ix, ry, iw, "Item moves per tick", cfg.depositClicksPerTick, 1, 1, 4, "", v -> cfg.depositClicksPerTick = v);
        ry += ROW;
        tog(ui, "deplearn", ix, ry, iw, "Learn drops of newly mined blocks", cfg.autoLearnDrops, b -> cfg.autoLearnDrops = b);
        cy += rows * ROW + 22;

        // --- filtry ---
        section(ui, "Item filters (" + cfg.depositFilter.size() + ")", x + 2, cy + 4);
        ui.button("addheld", x + cw - 130, cy, 130, 16, "ADD HELD ITEM", Ui.Style.GHOST, true, () -> {
            if (c.player == null) return;
            ItemStack held = c.player.getMainHandStack();
            if (held.isEmpty()) {
                AutoMiner.notifications.warn("HOLD AN ITEM FIRST");
                return;
            }
            String id = BlockInfo.itemId(held.getItem());
            if (cfg.depositFilter.contains(id)) {
                AutoMiner.notifications.warn("ITEM ALREADY IN FILTER");
            } else {
                cfg.depositFilter.add(id);
                dirty();
                AutoMiner.notifications.success("FILTER ADDED: " + BlockInfo.itemName(id));
            }
        });
        cy += 22;
        for (String id : new ArrayList<>(cfg.depositFilter)) {
            boolean on = !cfg.depositFilterOff.contains(id);
            boolean hov = ui.hover(x, cy, cw, 22);
            ui.rect(x, cy, cw, 22, 4, hov ? Draw.CARD_HOVER : Draw.CARD);
            ui.item(BlockInfo.itemIcon(id), x + 6, cy + 3);
            int tw = cw - 30 - 120;
            ui.text(ui.fit(BlockInfo.itemName(id), tw), x + 30, cy + 3, on ? Draw.TEXT : Draw.MUTED);
            ui.text(ui.fit(id + (cfg.learnedDrops.contains(id) ? "  [AUTO]" : ""), tw), x + 30, cy + 12, Draw.MUTED);
            ui.toggle("flt-" + id, x + cw - 100, cy + 4, on, b -> {
                if (b) {
                    cfg.depositFilterOff.remove(id);
                } else if (!cfg.depositFilterOff.contains(id)) {
                    cfg.depositFilterOff.add(id);
                }
                dirty();
            });
            ui.button("fdel-" + id, x + cw - 62, cy + 3, 56, 16, "REMOVE", Ui.Style.DANGER, true, () -> {
                cfg.depositFilter.remove(id);
                cfg.depositFilterOff.remove(id);
                cfg.learnedDrops.remove(id);
                dirty();
            });
            cy += 24;
        }
        ui.text("Items are only moved by normal chest clicks; nothing is dropped or deleted.", x + 2, cy + 2, Draw.MUTED);
        cy += 16;

        contentH = cy - top + 8;
        ui.scrollEnd();
    }
}
