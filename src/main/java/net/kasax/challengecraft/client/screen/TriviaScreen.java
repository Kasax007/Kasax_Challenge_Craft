package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.client.ui.Anim;
import net.kasax.challengecraft.client.ui.CraftUI;
import net.kasax.challengecraft.network.TriviaAnswerPacket;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.List;

@Environment(EnvType.CLIENT)
/** Modal answer screen shown when the trivia challenge asks a question. */
public class TriviaScreen extends Screen {
    private static final int TIMEOUT_SECONDS = 60;
    private static final long REVEAL_MS = 900L;

    private static final int PANEL_WIDTH = 320;
    private static final int PAD = 14;
    private static final int ANSWER_HEIGHT = 24;
    private static final int ANSWER_GAP = 6;
    private static final float QUESTION_SCALE = 1.25f;

    private final String question;
    private final List<String> answers;
    private final int correctIndex;
    private final long startTime;

    private boolean answered;
    private int selectedIndex = -1;
    private long answeredAt;

    private int panelX;
    private int panelY;
    private int panelHeight;
    private int questionTop;
    private List<OrderedText> questionLines;

    public TriviaScreen(String question, List<String> answers, int correctIndex) {
        super(Text.translatable("challengecraft.worldcreate.challenge36"));
        this.question = question;
        this.answers = answers;
        this.correctIndex = correctIndex;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        int innerWidth = PANEL_WIDTH - PAD * 2;

        this.questionLines = this.textRenderer.wrapLines(Text.of(question), (int) (innerWidth / QUESTION_SCALE));
        int questionBlock = (int) Math.ceil(this.questionLines.size() * this.textRenderer.fontHeight * QUESTION_SCALE) + 6;

        int headerBlock = this.textRenderer.fontHeight + 8;
        int answersBlock = answers.size() * (ANSWER_HEIGHT + ANSWER_GAP);
        int countdownBlock = 16;

        this.panelHeight = PAD + headerBlock + 4 + questionBlock + 6 + answersBlock + countdownBlock + PAD;
        this.panelX = (this.width - PANEL_WIDTH) / 2;
        this.panelY = (this.height - panelHeight) / 2;

        this.questionTop = panelY + PAD + headerBlock + 4;
        int answersTop = questionTop + questionBlock + 6;

        for (int i = 0; i < answers.size(); i++) {
            int index = i;
            int ay = answersTop + i * (ANSWER_HEIGHT + ANSWER_GAP);
            addDrawableChild(new AnswerButton(panelX + PAD, ay, PANEL_WIDTH - PAD * 2, ANSWER_HEIGHT, index, answers.get(i)));
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);

        CraftUI.frame(context, panelX, panelY, PANEL_WIDTH, panelHeight);
        CraftUI.sectionHeader(context, this.textRenderer, this.title, panelX + PAD, panelY + PAD,
                PANEL_WIDTH - PAD * 2, CraftUI.GOLD);

        // Title-scale question block, centered.
        context.getMatrices().push();
        context.getMatrices().scale(QUESTION_SCALE, QUESTION_SCALE, 1.0f);
        int lineHeight = this.textRenderer.fontHeight;
        for (int i = 0; i < questionLines.size(); i++) {
            OrderedText line = questionLines.get(i);
            float lineWidth = this.textRenderer.getWidth(line) * QUESTION_SCALE;
            float drawX = (this.width - lineWidth) / 2f / QUESTION_SCALE;
            float drawY = (questionTop + i * lineHeight * QUESTION_SCALE) / QUESTION_SCALE;
            context.drawText(this.textRenderer, line, (int) drawX, (int) drawY, CraftUI.TEXT_PRIMARY, false);
        }
        context.getMatrices().pop();

        // Countdown bar.
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        int remaining = Math.max(0, TIMEOUT_SECONDS - (int) elapsed);
        float fraction = MathHelper.clamp(remaining / (float) TIMEOUT_SECONDS, 0f, 1f);
        int barX = panelX + PAD;
        int barY = panelY + panelHeight - PAD - 6;
        int barWidth = PANEL_WIDTH - PAD * 2;
        int barColor = remaining <= 10 ? CraftUI.DANGER : CraftUI.INFO;
        CraftUI.progressBar(context, barX, barY, barWidth, 6, fraction, barColor);
        Text timer = Text.translatable("challengecraft.trivia.time_remaining", remaining);
        context.drawText(this.textRenderer, timer,
                barX + barWidth - this.textRenderer.getWidth(timer), barY - this.textRenderer.fontHeight - 2,
                remaining <= 10 ? CraftUI.DANGER : CraftUI.TEXT_SECONDARY, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        int remaining = Math.max(0, TIMEOUT_SECONDS - (int) elapsed);
        if (answered && System.currentTimeMillis() - answeredAt >= REVEAL_MS) {
            this.close();
        } else if (!answered && remaining <= 0) {
            this.close();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    private final class AnswerButton extends ClickableWidget {
        private final int index;
        private final Anim.Tween hover = new Anim.Tween(0f);

        private AnswerButton(int x, int y, int width, int height, int index, String label) {
            super(x, y, width, height, Text.of(label));
            this.index = index;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            CraftUI.CardState state = CraftUI.CardState.IDLE;
            int fill;
            int border;
            int accent;

            if (answered) {
                if (index == correctIndex) {
                    float p = Anim.pulse(600);
                    fill = CraftUI.mix(0xCC173126, 0xCC1F5138, p);
                    border = CraftUI.SUCCESS;
                    accent = CraftUI.SUCCESS;
                } else if (index == selectedIndex) {
                    fill = CraftUI.CardState.DANGER_STATE.fill;
                    border = CraftUI.CardState.DANGER_STATE.border;
                    accent = CraftUI.DANGER;
                } else {
                    fill = CraftUI.applyAlpha(CraftUI.SURFACE_RAISED, 0.5f);
                    border = CraftUI.BORDER;
                    accent = CraftUI.TEXT_MUTED;
                }
            } else {
                float g = hover.approach(isHovered() ? 1f : 0f, 12f);
                fill = CraftUI.mix(state.fill, 0xCC243449, g);
                border = CraftUI.mix(state.border, CraftUI.INFO, g);
                accent = CraftUI.mix(state.accent, 0xFFB3D5FF, g);
            }

            CraftUI.panel(context, getX(), getY(), getWidth(), getHeight(), fill, border, accent);
            int textColor = answered && index != correctIndex && index != selectedIndex
                    ? CraftUI.TEXT_MUTED : CraftUI.TEXT_PRIMARY;
            String trimmed = CraftUI.trimToWidth(TriviaScreen.this.textRenderer, getMessage().getString(), getWidth() - 20);
            context.drawText(TriviaScreen.this.textRenderer, Text.of(trimmed), getX() + 10,
                    getY() + (getHeight() - TriviaScreen.this.textRenderer.fontHeight) / 2 + 1, textColor, false);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            if (answered) {
                return;
            }
            answered = true;
            selectedIndex = index;
            answeredAt = System.currentTimeMillis();
            ClientPlayNetworking.send(new TriviaAnswerPacket(index));
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }
}
