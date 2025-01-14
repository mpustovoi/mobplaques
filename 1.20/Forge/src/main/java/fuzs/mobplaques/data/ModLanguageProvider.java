package fuzs.mobplaques.data;

import fuzs.mobplaques.client.handler.KeyBindingHandler;
import fuzs.puzzleslib.api.data.v1.AbstractLanguageProvider;
import net.minecraft.data.PackOutput;

public class ModLanguageProvider extends AbstractLanguageProvider {

    public ModLanguageProvider(PackOutput packOutput, String modId) {
        super(packOutput, modId);
    }

    @Override
    protected void addTranslations() {
        this.add(KeyBindingHandler.TOGGLE_PLAQUES_KEY_MAPPING, "Toggle Mob Plaques");
        this.add("key.togglePlaques.message", "Mob Plaques are set to: %s");
    }
}
