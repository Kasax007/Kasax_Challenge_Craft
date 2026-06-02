package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.network.TriviaAnswerPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

@Environment(EnvType.CLIENT)
/** Modal answer screen shown when the trivia challenge asks a question. */
public class TriviaScreen extends Screen {
    private final String question;
    private final List<String> answers;
    private final int correctIndex;
    private final long startTime;
    private static final int TIMEOUT_SECONDS = 60;

    public TriviaScreen(String question, List<String> answers, int correctIndex) {
        super(Component.translatable("challengecraft.worldcreate.challenge36"));
        this.question = question;
        this.answers = answers;
        this.correctIndex = correctIndex;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        int buttonWidth = 200;
        int buttonHeight = 20;
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        for (int i = 0; i < answers.size(); i++) {
            int index = i;
            this.addRenderableWidget(Button.builder(Component.nullToEmpty(answers.get(i)), button -> {
                ClientPlayNetworking.send(new TriviaAnswerPacket(index));
                this.onClose();
            }).bounds(centerX - buttonWidth / 2, centerY - 20 + i * 25, buttonWidth, buttonHeight).build());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        
        int bgWidth = 300;
        int bgHeight = 200;
        int x = (this.width - bgWidth) / 2;
        int y = (this.height - bgHeight) / 2;
        
        context.fill(x, y, x + bgWidth, y + bgHeight, 0xAA000000);
        context.outline(x, y, bgWidth, bgHeight, 0xFFFFFFFF);

        context.centeredText(this.font, this.title.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), this.width / 2, y + 10, 0xFFFFFF);
        
        List<net.minecraft.util.FormattedCharSequence> wrappedQuestion = this.font.split(Component.nullToEmpty(question), bgWidth - 20);
        int qY = y + 30;
        for (net.minecraft.util.FormattedCharSequence line : wrappedQuestion) {
            context.centeredText(this.font, line, this.width / 2, qY, 0xFFFFFF);
            qY += 10;
        }

        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        int remaining = Math.max(0, TIMEOUT_SECONDS - (int)elapsed);
        int timerColor = remaining <= 10 ? 0xFFFF5555 : 0xFFFFFFFF;
        context.centeredText(this.font, Component.translatable("challengecraft.trivia.time_remaining", remaining), this.width / 2, y + bgHeight - 15, timerColor);

        if (remaining <= 0) {
            this.onClose();
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
    
    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
