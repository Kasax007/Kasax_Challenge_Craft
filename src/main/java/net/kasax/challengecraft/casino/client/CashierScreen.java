package net.kasax.challengecraft.casino.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.casino.CasinoEconomy;
import net.kasax.challengecraft.casino.CasinoNet;
import net.kasax.challengecraft.casino.CasinoRegistry;
import net.kasax.challengecraft.casino.DeviceType;
import net.kasax.challengecraft.casino.EmcValues;
import net.kasax.challengecraft.casino.SlotGame;
import net.kasax.challengecraft.client.ui.Anim;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The croupier's cashier: deposit items for chips, buy back any item you have deposited before,
 * buy the casino devices, and look at the team's books. Deposits and purchases only work standing
 * at the croupier; opened from the wallet elsewhere it shows the account only.
 */
@Environment(EnvType.CLIENT)
public class CashierScreen extends Screen {
    private static final int W = 344, H = 238;
    private static final int TAB_DEPOSIT = 0, TAB_SHOP = 1, TAB_ACCOUNT = 2, TAB_DEVICES = 3;
    private static final int GOLD = 0xFFE3B35A;

    private CasinoNet.Cashier catalog;
    private int tab;
    private int x, y;
    private final Set<Integer> selected = new HashSet<>();
    private EditBox search;
    private String selectedItem = "";
    private int shopScroll;
    private final Anim.Tween balanceTween = new Anim.Tween(0f);
    private final long openedAt = System.currentTimeMillis();

    public CashierScreen(CasinoNet.Cashier catalog) {
        super(Component.translatable("challengecraft.casino.cashier.title"));
        this.catalog = catalog;
        this.tab = catalog.tab();
        if (EmcValues.all().isEmpty()) EmcValues.load();
        balanceTween.set(CasinoClientState.balance() / 100f);
    }

    public void update(CasinoNet.Cashier fresh) {
        this.catalog = fresh;
        this.selected.clear();
    }

    @Override
    protected void init() {
        x = (width - W) / 2;
        y = (height - H) / 2;
        String[] tabs = {"deposit", "shop", "account", "devices"};
        int tw = 78;
        for (int i = 0; i < tabs.length; i++) {
            int t = i;
            addRenderableWidget(new CasinoButton(x + 12 + i * (tw + 4), y + 30, tw, 16,
                    Component.translatable("challengecraft.casino.cashier.tab." + tabs[i]), GOLD,
                    () -> tab != t, () -> {
                tab = t;
                rebuildWidgets();
            }));
        }
        if (tab == TAB_DEPOSIT) {
            addRenderableWidget(new CasinoButton(x + 12, y + H - 28, 150, 18,
                    Component.translatable("challengecraft.casino.cashier.deposit_selected"), 0xFF7BE0A4,
                    () -> !selected.isEmpty(), this::depositSelected));
            addRenderableWidget(new CasinoButton(x + 168, y + H - 28, 164, 18,
                    Component.translatable("challengecraft.casino.cashier.deposit_all"), GOLD,
                    () -> true, () -> {
                CasinoClient.send(new CasinoNet.Action(CasinoNet.Action.DEPOSIT_ALL, 0L, 0, TAB_DEPOSIT, 0L, ""));
                selected.clear();
            }));
        } else if (tab == TAB_SHOP) {
            search = new EditBox(font, x + 12, y + 52, 150, 14, Component.translatable("challengecraft.casino.cashier.search"));
            search.setResponder(s -> shopScroll = 0);
            addRenderableWidget(search);
            int[] amounts = {1, 16, 64, 0};
            String[] labels = {"×1", "×16", "×64", "Max"};
            for (int i = 0; i < amounts.length; i++) {
                int n = amounts[i];
                addRenderableWidget(new CasinoButton(x + 180 + i * 39, y + H - 28, 36, 18, Component.literal(labels[i]),
                        GOLD, () -> !selectedItem.isEmpty(), () -> buy(n)));
            }
        } else if (tab == TAB_DEVICES) {
            DeviceType[] devices = {DeviceType.SLOT, DeviceType.CRASH, DeviceType.ROULETTE};
            for (int i = 0; i < devices.length; i++) {
                DeviceType d = devices[i];
                addRenderableWidget(new CasinoButton(x + 12 + i * 110, y + H - 30, 100, 18,
                        Component.translatable("challengecraft.casino.cashier.buy"), 0xFFB57BFF,
                        () -> canAfford(d), () -> CasinoClient.send(new CasinoNet.Action(CasinoNet.Action.BUY_DEVICE,
                        0L, d.ordinal(), TAB_DEVICES, 0L, ""))));
            }
        }
    }

    private void depositSelected() {
        StringBuilder sb = new StringBuilder();
        for (int s : selected) {
            if (sb.length() > 0) sb.append(',');
            sb.append(s);
        }
        CasinoClient.send(new CasinoNet.Action(CasinoNet.Action.DEPOSIT_SLOTS, 0L, 0, TAB_DEPOSIT, 0L, sb.toString()));
        selected.clear();
    }

    private void buy(int count) {
        CasinoClient.send(new CasinoNet.Action(CasinoNet.Action.BUY_ITEM, 0L, count, TAB_SHOP, 0L, selectedItem));
    }

    private boolean canAfford(DeviceType d) {
        Inventory inv = Minecraft.getInstance().player.getInventory();
        for (DeviceType.Cost c : d.price) {
            int n = 0;
            for (int i = 0; i < 36; i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(c.item()) && !s.isDamaged()) n += s.getCount();
            }
            if (n < c.count()) return false;
        }
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractBackground(ctx, mouseX, mouseY, delta);
        float open = Anim.easeOutCubic(Math.min(1f, (System.currentTimeMillis() - openedAt) / 220f));
        int dy = (int) ((1f - open) * 10);
        // Mahogany counter with a gold rail.
        CraftUI.frame(ctx, x, y + dy, W, H, 0xF21A0E0A, 0xFF4A2A16, GOLD);
        ctx.fill(x + 6, y + 24 + dy, x + W - 6, y + 25 + dy, CraftUI.applyAlpha(GOLD, 0.5f));
        ctx.text(font, this.title, x + 12, y + 10 + dy, GOLD, false);

        float target = CasinoClientState.balance() / 100f;
        float shown = balanceTween.approach(target, 6f);
        Component bal = Component.translatable("challengecraft.casino.cashier.balance",
                CasinoEconomy.formatFull((long) (shown * 100)));
        ctx.text(font, bal, x + W - 12 - font.width(bal), y + 10 + dy, 0xFFFFE9A8, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        switch (tab) {
            case TAB_DEPOSIT -> drawDeposit(ctx, mouseX, mouseY);
            case TAB_SHOP -> drawShop(ctx, mouseX, mouseY);
            case TAB_ACCOUNT -> drawAccount(ctx);
            case TAB_DEVICES -> drawDevices(ctx, mouseX, mouseY);
            default -> {
            }
        }
    }

    // ---- deposit ------------------------------------------------------------------------------

    private int slotAt(int mx, int my) {
        int gx = x + 12, gy = y + 60;
        for (int i = 0; i < 36; i++) {
            int col = i % 9;
            int row = i < 9 ? 3 : (i / 9) - 1;
            int sx = gx + col * 20, sy = gy + row * 20 + (i < 9 ? 6 : 0);
            if (mx >= sx && mx < sx + 18 && my >= sy && my < sy + 18) return i;
        }
        return -1;
    }

    private void drawDeposit(GuiGraphicsExtractor ctx, int mx, int my) {
        Inventory inv = minecraft.player.getInventory();
        int gx = x + 12, gy = y + 60;
        ctx.text(font, Component.translatable("challengecraft.casino.cashier.deposit_help"), gx, y + 50, CraftUI.TEXT_SECONDARY, false);
        long selectedValue = 0;
        int hovered = slotAt(mx, my);
        for (int i = 0; i < 36; i++) {
            int col = i % 9;
            int row = i < 9 ? 3 : (i / 9) - 1;
            int sx = gx + col * 20, sy = gy + row * 20 + (i < 9 ? 6 : 0);
            ItemStack s = inv.getItem(i);
            long value = CasinoEconomy.isWallet(s.getItem()) ? 0 : EmcValues.stackValue(s);
            boolean sel = selected.contains(i);
            int accent = sel ? GOLD : value > 0 ? 0xFF6A5A40 : 0xFF3A3035;
            ctx.fill(sx, sy, sx + 18, sy + 18, sel ? 0xFF3A2A10 : 0xFF150F0E);
            ctx.outline(sx, sy, 18, 18, accent);
            if (!s.isEmpty()) {
                ctx.item(s, sx + 1, sy + 1);
                if (s.getCount() > 1) {
                    String n = Integer.toString(s.getCount());
                    ctx.text(font, Component.literal(n), sx + 18 - font.width(n), sy + 10, 0xFFFFFFFF, true);
                }
                if (value <= 0) ctx.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x88300000);
            }
            if (sel) selectedValue += value;
        }
        int infoX = x + 202;
        CraftUI.panel(ctx, infoX, gy, W - 214, 106, 0xE0140E0C, 0xFF4A2A16, GOLD);
        if (hovered >= 0 && !inv.getItem(hovered).isEmpty()) {
            ItemStack s = inv.getItem(hovered);
            long unit = EmcValues.unitValue(s);
            ctx.item(s, infoX + 8, gy + 8);
            ctx.text(font, Component.literal(CraftUI.trimToWidth(font, s.getHoverName().getString(), W - 250)), infoX + 28, gy + 8, CraftUI.TEXT_PRIMARY, false);
            if (unit > 0 && !CasinoEconomy.isWallet(s.getItem())) {
                ctx.text(font, Component.translatable("challengecraft.casino.cashier.unit", CasinoEconomy.formatFull(unit * 100)), infoX + 28, gy + 19, GOLD, false);
                ctx.text(font, Component.translatable("challengecraft.casino.cashier.stack", CasinoEconomy.formatFull(unit * s.getCount() * 100)), infoX + 8, gy + 34, 0xFFFFE9A8, false);
            } else {
                ctx.text(font, Component.translatable("challengecraft.casino.cashier.worthless"), infoX + 28, gy + 19, CraftUI.DANGER, false);
                ctx.text(font, Component.translatable("challengecraft.casino.cashier.worthless_hint"), infoX + 8, gy + 34, CraftUI.TEXT_MUTED, false);
            }
        } else {
            CraftUI.drawWrapped(ctx, font, Component.translatable("challengecraft.casino.cashier.deposit_info"), infoX + 8, gy + 8, W - 230, CraftUI.TEXT_SECONDARY, 8);
        }
        Component sum = Component.translatable("challengecraft.casino.cashier.selected", CasinoEconomy.formatFull(selectedValue * 100));
        ctx.text(font, sum, x + 12, y + H - 42, selectedValue > 0 ? CraftUI.SUCCESS : CraftUI.TEXT_MUTED, false);
    }

    // ---- shop ---------------------------------------------------------------------------------

    private List<Integer> filteredCatalog() {
        List<Integer> out = new ArrayList<>();
        String q = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT).trim();
        for (int i = 0; i < catalog.items().size(); i++) {
            Item item = SlotGame.itemById(catalog.items().get(i));
            if (!q.isEmpty() && !new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)
                    && !catalog.items().get(i).contains(q)) continue;
            out.add(i);
        }
        return out;
    }

    private static final int SHOP_COLS = 8, SHOP_ROWS = 6;

    private void drawShop(GuiGraphicsExtractor ctx, int mx, int my) {
        List<Integer> list = filteredCatalog();
        int gx = x + 12, gy = y + 72;
        if (catalog.items().isEmpty()) {
            CraftUI.drawWrapped(ctx, font, Component.translatable("challengecraft.casino.cashier.shop_empty"), gx, gy, 150, CraftUI.TEXT_SECONDARY, 8);
        }
        int maxScroll = Math.max(0, (list.size() + SHOP_COLS - 1) / SHOP_COLS - SHOP_ROWS);
        shopScroll = Math.min(shopScroll, maxScroll);
        String hoveredId = null;
        long hoveredPrice = 0;
        for (int k = 0; k < SHOP_COLS * SHOP_ROWS; k++) {
            int idx = shopScroll * SHOP_COLS + k;
            if (idx >= list.size()) break;
            int i = list.get(idx);
            String id = catalog.items().get(i);
            int sx = gx + (k % SHOP_COLS) * 20, sy = gy + (k / SHOP_COLS) * 20;
            boolean sel = id.equals(selectedItem);
            boolean afford = CasinoClientState.balance() >= catalog.prices()[i] * 100;
            ctx.fill(sx, sy, sx + 18, sy + 18, sel ? 0xFF3A2A10 : 0xFF150F0E);
            ctx.outline(sx, sy, 18, 18, sel ? GOLD : afford ? 0xFF6A5A40 : 0xFF40282A);
            ctx.item(new ItemStack(SlotGame.itemById(id)), sx + 1, sy + 1);
            if (mx >= sx && mx < sx + 18 && my >= sy && my < sy + 18) {
                hoveredId = id;
                hoveredPrice = catalog.prices()[i];
            }
        }
        if (maxScroll > 0) {
            int barH = SHOP_ROWS * 20;
            int knob = Math.max(10, barH / (maxScroll + 1));
            int ky = gy + (barH - knob) * shopScroll / maxScroll;
            ctx.fill(gx + SHOP_COLS * 20 + 2, gy, gx + SHOP_COLS * 20 + 4, gy + barH, 0xFF2A1E16);
            ctx.fill(gx + SHOP_COLS * 20 + 2, ky, gx + SHOP_COLS * 20 + 4, ky + knob, GOLD);
        }
        int infoX = x + 180;
        CraftUI.panel(ctx, infoX, y + 52, W - 192, 128, 0xE0140E0C, 0xFF4A2A16, GOLD);
        String show = hoveredId != null ? hoveredId : selectedItem;
        if (show != null && !show.isEmpty()) {
            long price = hoveredId != null ? hoveredPrice : priceOf(selectedItem);
            ItemStack stack = new ItemStack(SlotGame.itemById(show));
            ctx.pose().pushMatrix();
            ctx.pose().translate(infoX + 12, y + 60);
            ctx.pose().scale(2f, 2f);
            ctx.item(stack, 0, 0);
            ctx.pose().popMatrix();
            ctx.text(font, Component.literal(CraftUI.trimToWidth(font, stack.getHoverName().getString(), W - 240)), infoX + 50, y + 62, CraftUI.TEXT_PRIMARY, false);
            ctx.text(font, Component.translatable("challengecraft.casino.cashier.price", CasinoEconomy.formatFull(price * 100)), infoX + 50, y + 74, GOLD, false);
            long canBuy = price > 0 ? CasinoClientState.balance() / (price * 100) : 0;
            ctx.text(font, Component.translatable("challengecraft.casino.cashier.can_buy", canBuy), infoX + 12, y + 98, CraftUI.TEXT_SECONDARY, false);
            if (!selectedItem.isEmpty()) {
                ctx.text(font, Component.translatable("challengecraft.casino.cashier.choose_amount"), infoX + 12, y + 162, CraftUI.TEXT_MUTED, false);
            }
        } else {
            CraftUI.drawWrapped(ctx, font, Component.translatable("challengecraft.casino.cashier.shop_info"), infoX + 8, y + 60, W - 208, CraftUI.TEXT_SECONDARY, 10);
        }
        ctx.text(font, Component.translatable("challengecraft.casino.cashier.known", catalog.items().size()), x + 12, y + H - 24, CraftUI.TEXT_MUTED, false);
    }

    private long priceOf(String id) {
        int i = catalog.items().indexOf(id);
        return i < 0 ? 0 : catalog.prices()[i];
    }

    // ---- account ------------------------------------------------------------------------------

    private void drawAccount(GuiGraphicsExtractor ctx) {
        CasinoNet.State s = CasinoClientState.state;
        int ax = x + 14, ay = y + 56;
        if (s == null) return;
        int secs = CasinoClientState.secondsToFee();
        CraftUI.panel(ctx, ax - 2, ay - 4, W - 24, 48, 0xE0140E0C, 0xFF4A2A16, s.total() < s.nextFee() ? CraftUI.DANGER : GOLD);
        CraftUI.drawCenteredScaled(ctx, font, Component.translatable("challengecraft.casino.account.next_fee",
                CasinoEconomy.formatFull(s.nextFee())), x + W / 2, ay + 8, 1.4f, 0xFFFFE9A8);
        ctx.centeredText(font, Component.translatable("challengecraft.casino.account.in",
                String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60), s.feeIndex()), x + W / 2, ay + 22, CraftUI.TEXT_SECONDARY);
        ctx.centeredText(font, Component.translatable(s.total() < s.nextFee() ? "challengecraft.casino.account.danger"
                : "challengecraft.casino.account.safe", CasinoEconomy.formatFull(s.total())), x + W / 2, ay + 32,
                s.total() < s.nextFee() ? CraftUI.DANGER : CraftUI.SUCCESS);

        int ly = ay + 52;
        ctx.text(font, Component.translatable("challengecraft.casino.account.team"), ax, ly, GOLD, false);
        ly += 12;
        for (CasinoNet.Member m : s.members()) {
            if (ly > y + H - 34) break;
            float share = s.total() > 0 ? (float) m.balance() / s.total() : 0f;
            ctx.text(font, Component.literal(CraftUI.trimToWidth(font, m.name(), 90)), ax, ly, CraftUI.TEXT_PRIMARY, false);
            CraftUI.progressBar(ctx, ax + 96, ly + 1, 110, 6, share, GOLD);
            String right = CasinoEconomy.format(m.balance()) + "  −" + CasinoEconomy.format(m.share());
            ctx.text(font, Component.literal(right), x + W - 14 - font.width(right), ly, CraftUI.TEXT_SECONDARY, false);
            ly += 12;
        }
        ctx.text(font, Component.translatable("challengecraft.casino.account.footer", s.knownCount()), ax, y + H - 24, CraftUI.TEXT_MUTED, false);
    }

    // ---- devices ------------------------------------------------------------------------------

    private void drawDevices(GuiGraphicsExtractor ctx, int mx, int my) {
        DeviceType[] devices = {DeviceType.SLOT, DeviceType.CRASH, DeviceType.ROULETTE};
        CasinoNet.State s = CasinoClientState.state;
        Inventory inv = minecraft.player.getInventory();
        for (int i = 0; i < devices.length; i++) {
            DeviceType d = devices[i];
            int cx = x + 12 + i * 110, cy = y + 54;
            boolean unlocked = s != null && s.unlocked().contains(d.id);
            int accent = unlocked ? 0xFF7BE0A4 : 0xFFB57BFF;
            CraftUI.panel(ctx, cx, cy, 100, H - 90, 0xE0140E0C, 0xFF3A2A40, accent);
            ctx.pose().pushMatrix();
            ctx.pose().translate(cx + 34, cy + 6);
            ctx.pose().scale(2f, 2f);
            ctx.item(new ItemStack(CasinoRegistry.item(d)), 0, 0);
            ctx.pose().popMatrix();
            ctx.centeredText(font, Component.translatable("block.challengecraft." + d.id), cx + 50, cy + 42, CraftUI.TEXT_PRIMARY);
            CraftUI.drawWrapped(ctx, font, Component.translatable("challengecraft.casino.device." + d.id + ".desc"),
                    cx + 6, cy + 54, 88, CraftUI.TEXT_MUTED, 4);
            int py = cy + 96;
            for (DeviceType.Cost c : d.price) {
                int have = 0;
                for (int k = 0; k < 36; k++) {
                    ItemStack st = inv.getItem(k);
                    if (st.is(c.item()) && !st.isDamaged()) have += st.getCount();
                }
                ctx.item(new ItemStack(c.item()), cx + 6, py - 4);
                ctx.text(font, Component.literal(have + "/" + c.count()), cx + 26, py, have >= c.count() ? CraftUI.SUCCESS : CraftUI.DANGER, false);
                py += 17;
            }
            ctx.centeredText(font, Component.translatable(unlocked ? "challengecraft.casino.cashier.unlocked"
                    : "challengecraft.casino.cashier.locked"), cx + 50, y + H - 46, accent);
        }
    }

    // ---- input --------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        int mx = (int) event.x(), my = (int) event.y();
        if (tab == TAB_DEPOSIT) {
            int slot = slotAt(mx, my);
            if (slot >= 0) {
                ItemStack s = minecraft.player.getInventory().getItem(slot);
                if (!s.isEmpty() && !CasinoEconomy.isWallet(s.getItem()) && EmcValues.stackValue(s) > 0) {
                    if (!selected.remove(slot)) selected.add(slot);
                    minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.kasax.challengecraft.casino.CasinoSounds.CHIP, selected.contains(slot) ? 1.2f : 0.9f, 0.6f));
                }
                return true;
            }
        } else if (tab == TAB_SHOP) {
            List<Integer> list = filteredCatalog();
            int gx = x + 12, gy = y + 72;
            for (int k = 0; k < SHOP_COLS * SHOP_ROWS; k++) {
                int idx = shopScroll * SHOP_COLS + k;
                if (idx >= list.size()) break;
                int sx = gx + (k % SHOP_COLS) * 20, sy = gy + (k / SHOP_COLS) * 20;
                if (mx >= sx && mx < sx + 18 && my >= sy && my < sy + 18) {
                    selectedItem = catalog.items().get(list.get(idx));
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (tab == TAB_SHOP) {
            shopScroll = Math.max(0, shopScroll - (int) Math.signum(verticalAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
