package net.kasax.challengecraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kasax.challengecraft.network.TriviaAnswerPacket;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

@Environment(EnvType.CLIENT)
public class TriviaScreen extends Screen {
    private final String question;
    private final List<String> answers;
    private final int correctIndex;
    private final long startTime;
    private static final int TIMEOUT_SECONDS = 60;

    public TriviaScreen(String question, List<String> answers, int correctIndex) {
        super(Text.literal("Minecraft Trivia"));
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
            this.addDrawableChild(ButtonWidget.builder(Text.literal(answers.get(i)), button -> {
                ClientPlayNetworking.send(new TriviaAnswerPacket(index));
                this.close();
            }).dimensions(centerX - buttonWidth / 2, centerY - 20 + i * 25, buttonWidth, buttonHeight).build());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        
        // Draw Overlay Background
        int bgWidth = 300;
        int bgHeight = 200;
        int x = (this.width - bgWidth) / 2;
        int y = (this.height - bgHeight) / 2;
        
        context.fill(x, y, x + bgWidth, y + bgHeight, 0xAA000000);
        context.drawBorder(x, y, bgWidth, bgHeight, 0xFFFFFFFF);

        // Draw Title
        context.drawCenteredTextWithShadow(this.textRenderer, this.title.copy().formatted(Formatting.GOLD, Formatting.BOLD), this.width / 2, y + 10, 0xFFFFFF);
        
        // Draw Question (wrapped)
        List<net.minecraft.text.OrderedText> wrappedQuestion = this.textRenderer.wrapLines(Text.literal(question), bgWidth - 20);
        int qY = y + 30;
        for (net.minecraft.text.OrderedText line : wrappedQuestion) {
            context.drawCenteredTextWithShadow(this.textRenderer, line, this.width / 2, qY, 0xFFFFFF);
            qY += 10;
        }

        // Draw Timer
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        int remaining = Math.max(0, TIMEOUT_SECONDS - (int)elapsed);
        String timerText = "Time remaining: " + remaining + "s";
        int timerColor = remaining <= 10 ? 0xFFFF5555 : 0xFFFFFFFF;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(timerText), this.width / 2, y + bgHeight - 15, timerColor);

        if (remaining <= 0) {
            this.close();
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
    
    @Override
    public boolean shouldPause() {
        return true;
    }
}
