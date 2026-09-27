package net.kasax.challengecraft.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.ChallengeCraft;
import net.kasax.challengecraft.ChallengeCraftClient;
import net.kasax.challengecraft.LevelManager;
import net.kasax.challengecraft.client.ui.Anim;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.client.widget.CraftButton;
import net.kasax.challengecraft.client.widget.SettableSlider;
import net.kasax.challengecraft.data.ChallengeSavedData;
import net.kasax.challengecraft.network.ChallengePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** In-world challenge editor used after a save already exists. */
public class ChallengeSelectionScreen extends Screen {
    private static final List<Integer> IDS = new ArrayList<>(List.of(
            1, 10, 16, 17, 18, 40, 45, 4, 5, 42, 6, 7, 37, 8, 13, 43, 11, 27, 12, 20, 26, 44, 21, 38, 41, 48, 30, 24, 28, 31, 25, 46, 32, 9, 47, 29, 33, 2, 3, 39, 34, 23, 49, 14, 36, 15, 35, 19, 22
    ));

    private final List<ChallengeCardWidget> cards = new ArrayList<>();
    private final List<ChallengeCardWidget> perkCards = new ArrayList<>();
    private final Map<Integer, ChallengeCardWidget> cardById = new HashMap<>();
    private final Map<Integer, AbstractSliderButton> sliderById = new HashMap<>();

    private SettableSlider maxHealthSlider;
    private SettableSlider slotsSlider;
    private SettableSlider mobHealthSlider;
    private SettableSlider doubleTroubleSlider;
    private SettableSlider gameSpeedSlider;
    private SettableSlider fibMinutesSlider;

    private WidgetScrollPanel scrollPanel;
    private CraftButton saveButton;
    /**
     * Whether the daily card at the top is the current choice.
     *
     * <p>Mutually exclusive with the challenge cards below by design: a daily IS a fixed ruleset, so
     * "the daily plus three of my own" is not a thing that can exist. Picking one side visibly
     * disables the other rather than silently ignoring it.
     */
    private boolean dailyChosen = false;
    private net.kasax.challengecraft.client.widget.DailyCardWidget dailyCard;
    private CraftButton saveAndRestartButton;

    private boolean needsRelayout;
    /** Search, filters and sections; survives the init() a resize or the code dialog triggers. */
    private final ChallengeBrowser browser = new ChallengeBrowser(() -> needsRelayout = true);

    private int panelX;
    private int panelTop;
    private int panelWidth;

    // sliderValue is the raw 0.0–1.0 knob position; sliderTicks the quantized gameplay unit.
    private double sliderValue;
    private int sliderTicks;
    private double slotsSliderValue;
    private int slotsSliderTicks;
    private double mobHealthSliderValue;
    private int mobHealthMultiplier;
    private double doubleTroubleSliderValue;
    private int doubleTroubleMultiplier;
    private double gameSpeedSliderValue;
    private int gameSpeedMultiplier;
    private double fibMinutesSliderValue;
    private int fibMinutes;

    private Component difficultyText = Component.empty();
    private double currentDifficulty = -1;

    /** Unsaved screen state carried across the init() that returning from the code dialog runs. */
    private record Restore(net.kasax.challengecraft.code.ChallengeCode setup, List<Integer> perks, boolean daily) {
    }

    private Restore restore;
    /**
     * The code last loaded here. Sent along while the ticked challenges and perks still match it, so
     * Save and Restart can use its seed. It never lifts a level lock.
     */
    private net.kasax.challengecraft.code.ChallengeCode importedCode;

    public ChallengeSelectionScreen() {
        super(Component.translatable("challengecraft.challenge_selection.title"));
    }

    /** What is on screen, as a code: the cards and sliders, and this world's seed. */
    private net.kasax.challengecraft.code.ChallengeCode currentCode() {
        if (dailyChosen) {
            return net.kasax.challengecraft.client.ChallengeCodeClient.ofDaily(
                    net.kasax.challengecraft.daily.DailyChallenges.get(
                            net.kasax.challengecraft.daily.DailyManager.todayIndex()));
        }
        Long seed = importedCode != null && matchesImported(getActiveIds()) && importedCode.hasSeed()
                ? importedCode.seed()
                : net.kasax.challengecraft.client.ChallengeCodeClient.currentWorldSeed();
        return net.kasax.challengecraft.code.ChallengeCode.of(getActiveIds(), getActivePerks(), sliderTicks,
                slotsSliderTicks, mobHealthMultiplier, doubleTroubleMultiplier, gameSpeedMultiplier, fibMinutes, seed);
    }

    private boolean matchesImported(List<Integer> ids) {
        return importedCode != null
                && new java.util.HashSet<>(ids).equals(new java.util.HashSet<>(importedCode.challengeIds()))
                && new java.util.HashSet<>(getActivePerks()).equals(new java.util.HashSet<>(importedCode.perkIds()));
    }

    private void openCodeScreen() {
        net.kasax.challengecraft.code.ChallengeCode now = currentCode();
        // Snapshot the cards themselves (a daily's code is not what the cards show).
        this.restore = new Restore(net.kasax.challengecraft.code.ChallengeCode.of(getActiveIds(), getActivePerks(),
                sliderTicks, slotsSliderTicks, mobHealthMultiplier, doubleTroubleMultiplier, gameSpeedMultiplier,
                fibMinutes, null), getActivePerks(), dailyChosen);
        this.minecraft.setScreenAndShow(new ChallengeCodeScreen(this, now,
                Component.translatable("challengecraft.code.hint.in_world"), this::loadSetup));
    }

    private void openPresetScreen() {
        net.kasax.challengecraft.code.ChallengeCode now = currentCode();
        this.restore = new Restore(net.kasax.challengecraft.code.ChallengeCode.of(getActiveIds(), getActivePerks(),
                sliderTicks, slotsSliderTicks, mobHealthMultiplier, doubleTroubleMultiplier, gameSpeedMultiplier,
                fibMinutes, null), getActivePerks(), dailyChosen);
        this.minecraft.setScreenAndShow(new PresetScreen(this, now,
                Component.translatable("challengecraft.code.hint.in_world"), this::loadSetup));
    }

    /** A code or preset was chosen: put it on the cards and come back here. */
    private void loadSetup(net.kasax.challengecraft.code.ChallengeCode code) {
        this.importedCode = code;
        this.restore = new Restore(code, code.perkIds(), false);
        this.minecraft.setScreenAndShow(this);
    }

    private void updateDifficultyText() {
        // With the daily picked, every card below is cleared on purpose — so reading the cards
        // would report 0.00 and a payout of nothing for a run that is usually one of the hardest
        // on offer. The numbers have to come from the rotation entry itself.
        var daily = dailyChosen ? net.kasax.challengecraft.daily.DailyChallenges
                .get(net.kasax.challengecraft.daily.DailyManager.todayIndex()) : null;
        List<Integer> activeIds = daily != null ? daily.challengeIds() : getActiveIds();
        List<Integer> activePerks = daily != null ? List.of() : getActivePerks();

        if (net.kasax.challengecraft.ChallengeManager.hasConflict(activeIds, activePerks)) {
            this.currentDifficulty = -1;
            this.difficultyText = Component.translatable("challengecraft.warning.conflict");
        } else {
            int playerCount = 0;
            if (this.minecraft != null && this.minecraft.level != null) {
                playerCount = this.minecraft.level.players().size();
            }
            int UNSET = net.kasax.challengecraft.daily.DailyEntry.UNSET;
            int hearts = daily == null || daily.maxHearts() == UNSET ? sliderTicks : daily.maxHearts();
            int slots = daily == null || daily.inventorySlots() == UNSET
                    ? slotsSliderTicks : daily.inventorySlots();
            int mobHp = daily == null || daily.mobHealthMultiplier() == UNSET
                    ? mobHealthMultiplier : daily.mobHealthMultiplier();
            int speed = daily == null || daily.gameSpeedMultiplier() == UNSET
                    ? gameSpeedMultiplier : daily.gameSpeedMultiplier();
            int twice = daily == null || daily.doubleTroubleMultiplier() == UNSET
                    ? doubleTroubleMultiplier : daily.doubleTroubleMultiplier();
            double total = net.kasax.challengecraft.ChallengeManager.calculateTotalDifficulty(activeIds, hearts, slots, mobHp, speed, twice, playerCount, activePerks);
            this.currentDifficulty = total;
            this.difficultyText = Component.translatable("challengecraft.worldcreate.difficulty", String.format("%.2f", total));
        }
    }

    /** Projected lifetime XP payout on successful completion (matches the server formula). */
    private long projectedPayout() {
        return currentDifficulty <= 0 ? 0 : Math.round(100.0 * currentDifficulty);
    }

    @Override
    protected void init() {
        super.init();
        cards.clear();
        perkCards.clear();
        cardById.clear();
        sliderById.clear();

        // Cards snapshot the player's level at construction; refresh first so opening this
        // screen right after joining (before the XP sync lands) doesn't show everything locked.
        ChallengeCraftClient.refreshLocalPlayerXp();

        Minecraft client = Minecraft.getInstance();
        MinecraftServer server = client.getSingleplayerServer();

        List<Integer> active;
        List<Integer> activePerks;
        int savedMaxHeartsTicks;
        int savedSlots;
        int savedMobHealthMult;
        int savedDoubleTroubleMult;
        int savedGameSpeedMult;
        int savedFibMinutes;

        if (server != null) {
            ChallengeSavedData data = ChallengeSavedData.get(server.overworld());
            active = data.getActive();
            activePerks = data.getActivePerks();
            savedMaxHeartsTicks = data.getMaxHeartsTicks();
            savedSlots = data.getLimitedInventorySlots();
            savedMobHealthMult = data.getMobHealthMultiplier();
            savedDoubleTroubleMult = data.getDoubleTroubleMultiplier();
            savedGameSpeedMult = data.getGameSpeedMultiplier();
            savedFibMinutes = data.getForceItemBattleMinutes();
        } else {
            active = ChallengeCraftClient.LAST_CHOSEN;
            activePerks = ChallengeCraftClient.SELECTED_PERKS;
            savedMaxHeartsTicks = ChallengeCraftClient.SELECTED_MAX_HEARTS;
            savedSlots = ChallengeCraftClient.SELECTED_LIMITED_INVENTORY;
            savedMobHealthMult = ChallengeCraftClient.SELECTED_MOB_HEALTH_MULTIPLIER;
            savedDoubleTroubleMult = ChallengeCraftClient.SELECTED_DOUBLE_TROUBLE_MULTIPLIER;
            savedGameSpeedMult = ChallengeCraftClient.SELECTED_GAME_SPEED_MULTIPLIER;
            savedFibMinutes = ChallengeCraftClient.SELECTED_FIB_MINUTES;
        }

        if (savedMaxHeartsTicks <= 0) savedMaxHeartsTicks = 20;
        if (savedSlots <= 0) savedSlots = 36;
        if (savedMobHealthMult <= 0) savedMobHealthMult = 1;
        if (savedDoubleTroubleMult <= 0) savedDoubleTroubleMult = 2;
        if (savedGameSpeedMult <= 0) savedGameSpeedMult = 1;
        if (savedFibMinutes <= 0) savedFibMinutes = 60;

        // Back from the code dialog: init() runs again and would reset every card to the saved
        // world state, throwing away what was ticked (or just loaded from a code) but not saved yet.
        if (restore != null) {
            active = restore.setup().challengeIds();
            activePerks = restore.perks();
            savedMaxHeartsTicks = restore.setup().maxHearts();
            savedSlots = restore.setup().inventorySlots();
            savedMobHealthMult = restore.setup().mobHealth();
            savedDoubleTroubleMult = restore.setup().doubleTrouble();
            savedGameSpeedMult = restore.setup().gameSpeed();
            savedFibMinutes = restore.setup().fibMinutes();
            dailyChosen = restore.daily();
            restore = null;
        }

        sliderTicks = savedMaxHeartsTicks;
        sliderValue = (sliderTicks - 1) / 19.0;
        slotsSliderTicks = savedSlots;
        slotsSliderValue = (slotsSliderTicks - 1) / 35.0;
        mobHealthMultiplier = savedMobHealthMult;
        mobHealthSliderValue = (mobHealthMultiplier - 1) / 99.0;
        doubleTroubleMultiplier = savedDoubleTroubleMult;
        doubleTroubleSliderValue = (doubleTroubleMultiplier - 2) / 8.0;
        gameSpeedMultiplier = savedGameSpeedMult;
        gameSpeedSliderValue = (gameSpeedMultiplier - 1) / 9.0;
        fibMinutes = savedFibMinutes;
        fibMinutesSliderValue = (fibMinutes - 15) / 165.0;

        this.panelWidth = 300;
        this.panelX = width / 2 - panelWidth / 2;
        // Search | Category | Show, between the difficulty line and the list.
        int searchY = 36;
        int buttonW = 70;
        addRenderableWidget(browser.searchBox(this.font, panelX, searchY, panelWidth - 2 * buttonW - 8, 16));
        addRenderableWidget(browser.categoryButton(panelX + panelWidth - 2 * buttonW - 4, searchY - 1, buttonW, 18));
        addRenderableWidget(browser.showButton(panelX + panelWidth - buttonW, searchY - 1, buttonW, 18));
        this.panelTop = searchY + 22;
        int panelBottomReserved = 48;
        int panelHeight = Math.max(60, height - panelTop - panelBottomReserved);

        this.scrollPanel = new WidgetScrollPanel(panelX, panelTop, panelWidth, panelHeight, Component.empty());
        addRenderableWidget(this.scrollPanel);


        buildSliders();

        for (int id : IDS) {
            boolean isOn = active.contains(id);
            ChallengeCardWidget card = new ChallengeCardWidget(0, 0, 139, 26, id, isOn, val -> {
                updateSaveButton();
                updateDifficultyText();
                refreshConflicts();
            });
            cards.add(card);
            cardById.put(id, card);
        }

        for (int perkId : LevelManager.ALL_PERKS) {
            if (perkId == LevelManager.PERK_INFINITY_WEAPON
                    && LevelManager.getStars(ChallengeCraftClient.LOCAL_PLAYER_XP) < 20) {
                continue;
            }
            boolean isOn = activePerks.contains(perkId);
            ChallengeCardWidget perkCard = new ChallengeCardWidget(0, 0, 139, 26, perkId, isOn, val -> {
                updateSaveButton();
                updateDifficultyText();
                refreshConflicts();
            });
            perkCards.add(perkCard);
        }

        int saveY = panelTop + panelHeight + 12;
        // Code | Presets | Save | Save and Restart. 386 wide, so the row still fits a 480-wide screen
        // (1080p at GUI scale 4).
        int rowX = width / 2 - 193;
        addRenderableWidget(new CraftButton(rowX, saveY, 64, 20,
                Component.translatable("challengecraft.code.button"), CraftButton.Style.NEUTRAL,
                btn -> openCodeScreen()));
        addRenderableWidget(new CraftButton(rowX + 70, saveY, 64, 20,
                Component.translatable("challengecraft.preset.button"), CraftButton.Style.NEUTRAL,
                btn -> openPresetScreen()));
        this.saveButton = new CraftButton(rowX + 140, saveY, 120, 20,
                Component.translatable("challengecraft.challenge_selection.save"), CraftButton.Style.PRIMARY,
                btn -> {
                    // A daily needs a fresh world, so plain "Save" cannot serve it — it would apply
                    // the ruleset to the world you are standing in, with the wrong seed and a
                    // history. Only Save and Restart can honour it.
                    if (dailyChosen) {
                        client.setScreenAndShow(new ConfirmRestartScreen(this, this::sendDailyRestart));
                        return;
                    }
                    sendChallengePacket(false);
                    client.setScreenAndShow(null);
                });
        this.saveAndRestartButton = new CraftButton(rowX + 266, saveY, 120, 20,
                Component.translatable("challengecraft.challenge_selection.save_restart"), CraftButton.Style.NEUTRAL,
                btn -> client.setScreenAndShow(new ConfirmRestartScreen(this,
                        dailyChosen ? this::sendDailyRestart : () -> sendChallengePacket(true))));
        addRenderableWidget(this.saveButton);
        addRenderableWidget(this.saveAndRestartButton);

        layout();
        updateSaveButton();
        updateDifficultyText();
        refreshConflicts();
    }

    private void refreshConflicts() {
        List<ChallengeCardWidget> all = new ArrayList<>(cards);
        all.addAll(perkCards);
        ChallengeBrowser.refreshConflicts(all, getActiveIds(), getActivePerks());
    }

    private void buildSliders() {
        this.maxHealthSlider = new SettableSlider(0, 0, 139, 20, getHealthSliderText(0.5 + (sliderValue * 9.5)), sliderValue) {
            @Override protected void updateMessage() { setMessage(getHealthSliderText(0.5 + (this.value * 9.5))); }
            @Override protected void applyValue() {
                sliderTicks = (int) (Math.round(this.value * 19) + 1);
                this.value = (sliderTicks - 1) / 19.0;
                updateDifficultyText();
            }
        };
        this.slotsSlider = new SettableSlider(0, 0, 139, 20, getSlotsSliderText(slotsSliderTicks), slotsSliderValue) {
            @Override protected void updateMessage() { setMessage(getSlotsSliderText((int) (1 + (this.value * 35)))); }
            @Override protected void applyValue() {
                slotsSliderTicks = (int) (Math.round(this.value * 35) + 1);
                this.value = (slotsSliderTicks - 1) / 35.0;
                updateDifficultyText();
            }
        };
        this.mobHealthSlider = new SettableSlider(0, 0, 139, 20, getMobHealthSliderText(mobHealthMultiplier), mobHealthSliderValue) {
            @Override protected void updateMessage() { setMessage(getMobHealthSliderText(1 + (this.value * 99))); }
            @Override protected void applyValue() {
                mobHealthMultiplier = (int) (Math.round(this.value * 99) + 1);
                this.value = (mobHealthMultiplier - 1) / 99.0;
                updateDifficultyText();
            }
        };
        this.doubleTroubleSlider = new SettableSlider(0, 0, 139, 20, getDoubleTroubleSliderText(doubleTroubleMultiplier), doubleTroubleSliderValue) {
            @Override protected void updateMessage() { setMessage(getDoubleTroubleSliderText(2 + (this.value * 8))); }
            @Override protected void applyValue() {
                doubleTroubleMultiplier = (int) (Math.round(this.value * 8) + 2);
                this.value = (doubleTroubleMultiplier - 2) / 8.0;
                updateDifficultyText();
            }
        };
        this.gameSpeedSlider = new SettableSlider(0, 0, 139, 20, getGameSpeedSliderText(gameSpeedMultiplier), gameSpeedSliderValue) {
            @Override protected void updateMessage() { setMessage(getGameSpeedSliderText(1 + (this.value * 9))); }
            @Override protected void applyValue() {
                gameSpeedMultiplier = (int) (Math.round(this.value * 9) + 1);
                this.value = (gameSpeedMultiplier - 1) / 9.0;
                updateDifficultyText();
            }
        };

        // 15–180 minutes in 5-minute steps (33 steps over a 165-minute range).
        this.fibMinutesSlider = new SettableSlider(0, 0, 139, 20, getFibMinutesSliderText(fibMinutes), fibMinutesSliderValue) {
            @Override protected void updateMessage() { setMessage(getFibMinutesSliderText(15 + (int) Math.round(this.value * 33) * 5)); }
            @Override protected void applyValue() {
                fibMinutes = 15 + (int) Math.round(this.value * 33) * 5;
                this.value = (fibMinutes - 15) / 165.0;
                updateDifficultyText();
            }
        };

        sliderById.put(7, maxHealthSlider);
        sliderById.put(12, slotsSlider);
        sliderById.put(24, mobHealthSlider);
        sliderById.put(35, doubleTroubleSlider);
        sliderById.put(37, gameSpeedSlider);
        sliderById.put(45, fibMinutesSlider);
    }

    /** Rebuilds the scroll-panel contents from the current expanded/collapsed state. */
    /**
     * Sends the daily's ruleset with a restart.
     *
     * <p>Separate from {@link #sendChallengePacket} because the values come from the rotation, not
     * from the cards on screen — reusing that method would send whatever the player happened to have
     * ticked. The seed is applied server-side from the same rotation entry, so nothing about the
     * daily depends on the client agreeing.
     */
    private void sendDailyRestart() {
        net.kasax.challengecraft.client.DailyClientState.arm();
        var entry = net.kasax.challengecraft.client.DailyClientState.entry();
        if (entry != null) {
            sendDailyPacket(entry);
        }
    }

    private void sendDailyPacket(net.kasax.challengecraft.daily.DailyEntry entry) {
        int index = net.kasax.challengecraft.client.DailyClientState.index();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new net.kasax.challengecraft.network.ChallengePacket(
                        entry.challengeIds(),
                        entry.maxHearts() == net.kasax.challengecraft.daily.DailyEntry.UNSET ? 20 : entry.maxHearts(),
                        entry.inventorySlots() == net.kasax.challengecraft.daily.DailyEntry.UNSET ? 36 : entry.inventorySlots(),
                        entry.mobHealthMultiplier() == net.kasax.challengecraft.daily.DailyEntry.UNSET ? 1 : entry.mobHealthMultiplier(),
                        entry.doubleTroubleMultiplier() == net.kasax.challengecraft.daily.DailyEntry.UNSET ? 2 : entry.doubleTroubleMultiplier(),
                        entry.gameSpeedMultiplier() == net.kasax.challengecraft.daily.DailyEntry.UNSET ? 1 : entry.gameSpeedMultiplier(),
                        ChallengeCraftClient.SELECTED_FIB_MINUTES,
                        java.util.List.of(),
                        true,
                        index));
        net.kasax.challengecraft.client.DailyClientState.clear();
    }

    /**
     * Picking the daily clears every hand-picked challenge; they cannot both be true.
     *
     * <p>The card stays clickable at all times. Greying it out while challenges were ticked looked
     * like the right way to say "either/or" and was a dead end in practice: opened in a running
     * world the challenges are already on, so the card was born disabled and the daily could never
     * be reached. Enforcing the rule through the click — take the daily, lose the selection — says
     * the same thing and always has a way out.
     */
    private void toggleDaily() {
        dailyChosen = !dailyChosen;
        // The lock has to be (un)set here, not only in layout(): toggling does not re-lay-out, so
        // without this the cards below stayed clickable after the daily was picked.
        for (ChallengeCardWidget card : cards) {
            if (dailyChosen) {
                card.setActive(false);
            }
            card.setRuleLocked(dailyChosen);
        }
        for (ChallengeCardWidget perkCard : perkCards) {
            if (dailyChosen) {
                perkCard.setActive(false);
            }
            perkCard.setRuleLocked(dailyChosen);
        }
        updateDifficultyText();
        updateSaveButton();
    }

    private void layout() {
        double keep = scrollPanel.scrollAmount();
        this.scrollPanel.clearChildren();

        int spacing = 6;
        int headerW = panelWidth - 16;
        int x0 = panelX + 8;
        int y = panelTop + 6;

        // Rebuilt each layout pass so it always sits at the top and always carries the current
        // width; the widget itself is cheap and reads today's rotation in its constructor. Hidden
        // while searching: it is not a challenge and matches nothing typed.
        if (!browser.isFiltering()) {
            this.dailyCard = new net.kasax.challengecraft.client.widget.DailyCardWidget(
                            x0, y, headerW, this::toggleDaily)
                    .withSelected(() -> dailyChosen);
            scrollPanel.addChild(this.dailyCard);
            y += net.kasax.challengecraft.client.widget.DailyCardWidget.HEIGHT + spacing + 4;
        }

        for (ChallengeCardWidget card : cards) {
            card.setRuleLocked(dailyChosen);
        }
        for (ChallengeCardWidget perkCard : perkCards) {
            perkCard.setRuleLocked(dailyChosen);
        }
        browser.layout(scrollPanel, x0, y, headerW, spacing, 26, IDS, cardById, sliderById, perkCards);
        scrollPanel.setScrollAmount(keep);
    }

    private void sendChallengePacket(boolean restart) {
        List<Integer> newActive = getActiveIds();
        List<Integer> newPerks = getActivePerks();

        int heartsTicks = newActive.contains(7) ? this.sliderTicks : 0;
        int slotticks = newActive.contains(12) ? this.slotsSliderTicks : 0;
        int mobHealthMult = newActive.contains(24) ? this.mobHealthMultiplier : 1;
        int doubleMult = newActive.contains(35) ? this.doubleTroubleMultiplier : 2;
        int gameSpeedMult = newActive.contains(37) ? this.gameSpeedMultiplier : 1;
        int fibMin = newActive.contains(45) ? this.fibMinutes : 60;

        ChallengeCraft.LOGGER.info(
                // gameSpeed was missing a placeholder, so every value after it printed shifted:
                // the log showed doubleTrouble's value under "restart" and dropped restart entirely.
                "[Client:Selection] sending ChallengePacket → active = {} , perks = {}, maxHearts ticks = {}, slots = {}, mobHealth = {}, doubleTrouble = {}, gameSpeed = {}, restart = {}",
                newActive, newPerks, heartsTicks, slotticks, mobHealthMult, doubleMult, gameSpeedMult, restart
        );

        // Only while the ticked challenges are still exactly the code's: a code the player has since
        // edited is their own selection again, level locks included.
        String code = matchesImported(newActive) ? importedCode.encode() : "";
        ClientPlayNetworking.send(new ChallengePacket(newActive, heartsTicks, slotticks, mobHealthMult, doubleMult,
                gameSpeedMult, fibMin, newPerks, restart, -1, code));
    }

    private List<Integer> getActiveIds() {
        List<Integer> ids = new ArrayList<>();
        for (ChallengeCardWidget card : cards) {
            if (card.isActive()) {
                ids.add(card.getChallengeId());
            }
        }
        return ids;
    }

    private List<Integer> getActivePerks() {
        List<Integer> ids = new ArrayList<>();
        for (ChallengeCardWidget card : perkCards) {
            if (card.isActive()) {
                ids.add(card.getChallengeId());
            }
        }
        return ids;
    }

    private void updateSaveButton() {
        boolean noConflict = !net.kasax.challengecraft.ChallengeManager.hasConflict(getActiveIds(), getActivePerks());
        if (saveButton != null) {
            saveButton.active = noConflict;
        }
        if (saveAndRestartButton != null) {
            saveAndRestartButton.active = noConflict;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        this.minecraft.setScreenAndShow(null);
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractBackground(ctx, mouseX, mouseY, delta);
        // Frame behind the scrollable card list.
        CraftUI.frame(ctx, panelX - 4, panelTop - 4, panelWidth + 8,
                (scrollPanel != null ? scrollPanel.getHeight() : 0) + 8);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (needsRelayout) {
            layout();
            needsRelayout = false;
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);

        ctx.centeredText(this.font, this.title, width / 2, 8, CraftUI.TEXT_PRIMARY);

        Component conflict = ChallengeBrowser.conflictLine(getActiveIds(), getActivePerks());
        if (conflict != null) {
            ctx.centeredText(this.font, conflict, width / 2, 24, CraftUI.DANGER);
        } else {
            var combined = Component.translatable("challengecraft.worldcreate.difficulty", String.format("%.2f", currentDifficulty))
                    .copy().append(Component.literal("   •   "))
                    .append(Component.translatable("challengecraft.worldcreate.xp_payout", String.format(Locale.ROOT, "%,d", projectedPayout())));
            // Say that a loaded code is in play, and that its seed only takes effect with a restart.
            if (!dailyChosen && matchesImported(getActiveIds()) && importedCode.hasSeed()) {
                combined.append(Component.literal("   •   ")).append(
                        Component.translatable("challengecraft.code.loaded_seed", String.valueOf(importedCode.seed())));
            }
            ctx.centeredText(this.font, combined, width / 2, 24, CraftUI.WARNING);
        }
    }

    private static Component getHealthSliderText(double hearts) {
        return Component.translatable("challengecraft.slider.health", String.format(Locale.ROOT, "%.1f", hearts));
    }

    private static Component getSlotsSliderText(double slots) {
        return Component.translatable("challengecraft.slider.slots", String.format(Locale.ROOT, "%.0f", slots));
    }

    private static Component getMobHealthSliderText(double multiplier) {
        return Component.translatable("challengecraft.slider.mob_health", String.format(Locale.ROOT, "%.0f", multiplier));
    }

    private static Component getDoubleTroubleSliderText(double multiplier) {
        return Component.translatable("challengecraft.slider.double_trouble", String.format(Locale.ROOT, "%.0f", multiplier));
    }

    private static Component getGameSpeedSliderText(double multiplier) {
        return Component.translatable("challengecraft.slider.game_speed", String.format(Locale.ROOT, "%.0f", multiplier));
    }

    private static Component getFibMinutesSliderText(int minutes) {
        return Component.translatable("challengecraft.slider.fib_minutes", minutes);
    }

    /** Collapsible category header row inside the scroll panel. */
}
