package net.kasax.challengecraft.casino;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.util.Locale;

/**
 * A short public-service note shown to every player who joins a world with "The House Always Wins"
 * active: this is a game, real gambling can become an addiction, and where to find help. Only the
 * joining player sees it. The help links follow the player's game language — German players get the
 * German federal helpline, everyone else the international services.
 */
public final class GamblingNotice {
    private GamblingNotice() {
    }

    private static MutableComponent link(String label, String url) {
        return Component.literal(label).withStyle(Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(url))));
    }

    public static void send(ServerPlayer player) {
        String lang = player.clientInformation().language();
        boolean german = lang != null && lang.toLowerCase(Locale.ROOT).startsWith("de");
        MutableComponent links;
        if (german) {
            links = Component.empty()
                    .append(link("check-dein-spiel.de", "https://www.check-dein-spiel.de/"))
                    .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(link("BZgA-Telefon 0800 1 37 27 00", "https://www.bzga.de/service/infotelefone/gluecksspielsucht/"));
        } else {
            links = Component.empty()
                    .append(link("BeGambleAware.org", "https://www.begambleaware.org/"))
                    .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(link("GamblingTherapy.org", "https://www.gamblingtherapy.org/"));
        }
        player.sendSystemMessage(Component.empty()
                .append(Component.translatable("challengecraft.casino.psa.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal("\n"))
                .append(Component.translatable("challengecraft.casino.psa.body").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\n"))
                .append(Component.translatable("challengecraft.casino.psa.help").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" "))
                .append(links));
    }
}
