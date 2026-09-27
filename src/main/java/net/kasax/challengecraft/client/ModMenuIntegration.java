package net.kasax.challengecraft.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kasax.challengecraft.client.screen.TimerSettingsScreen;

/**
 * The "Config" button next to Challenge Craft in Mod Menu opens the HUD settings (run timer and
 * challenge cards).
 *
 * <p>Mod Menu is only a compile-time dependency. Fabric loads the {@code modmenu} entrypoint only
 * when Mod Menu itself is installed, so without it this class is never touched and nothing is
 * missing at runtime. The same screen stays reachable from Video Settings for everyone else.
 */
@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return TimerSettingsScreen::new;
    }
}
