package net.raconteur.rpupdater;

import de.keksuccino.fancymenu.customization.action.ActionRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.ModContainer;
import net.raconteur.rpupdater.config.ModConfigs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import de.keksuccino.fancymenu.customization.variables.VariableHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

@Mod(UpdateRPMod.MOD_ID)
public class UpdateRPMod {
    public static final Logger LOGGER = LoggerFactory.getLogger("rpupdater");
    public static final String MOD_ID = "rpupdater";
    private static final Path txtPath = Paths.get("config", "fancymenu", "customizablemenus.txt");
    private static final String menuName = "net.minecraft.client.gui.screens.TitleScreen {";

    public UpdateRPMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Initializing RP Updater!");
        modEventBus.addListener(this::setup);
        ActionRegistry.register(new UpdateRPAction());
        ActionRegistry.register(new VerifyRPVersionsAction());
        modContainer.registerConfig(ModConfig.Type.COMMON, ModConfigs.SPEC, MOD_ID + "-common.toml");
    }

    private void setup(FMLCommonSetupEvent event) {
        String regex = ModConfigs.RP_NAME_REGEX.get();
        String owner = ModConfigs.REPO_OWNER.get();
        String repo  = ModConfigs.REPO_NAME.get();

        LOGGER.info("Config loaded: regex={}, owner={}, repo={}", regex, owner, repo);
        addConfigToFancyMenu();
        ensureCustomisable();

        //Sets up the variables if they don't already exist (required for first-load button visibility)
        if (VariableHandler.getVariable("rp_actual_version") == null) {
            VariableHandler.setVariable("rp_actual_version", "Null");
        }

        if (VariableHandler.getVariable("rp_latest_available_version") == null) {
            VariableHandler.setVariable("rp_latest_available_version", "Please set up config file");
        }

        if (VariableHandler.getVariable("rp_need_update") == null) {
            VariableHandler.setVariable("rp_need_update", "true");
        }
    }

    //Keeps the main menu customisation always enabled (otherwise elements won't show)
    private static void ensureCustomisable() {
        String toAppend = "\n" + menuName + "\n}\n";
        try {
            Files.createDirectories(txtPath.getParent());
            //Creates the txt before FM does to set customisation
            if (Files.notExists(txtPath)) {
                Files.writeString(
                        txtPath,
                        "type = customizablemenus\n" + toAppend,
                        StandardCharsets.UTF_8
                );
            }
            String content = Files.readString(txtPath, StandardCharsets.UTF_8);
            //Make the main menu customisable if it isn't already (messy)
            if (!content.contains(menuName)) {
                UpdateRPMod.LOGGER.info("Allowing main menu customisation");

                Files.writeString(
                        txtPath,
                        toAppend,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.APPEND
                );
            }
        } catch (IOException e) {
            UpdateRPMod.LOGGER.error("Failed to initialize customizablemenus.txt", e);
        }
    }

    public static void addConfigToFancyMenu() {

        Path targetDir = Paths.get("config", "fancymenu", "customization");
        Path targetFile = targetDir.resolve("UpdateRP.txt");

        if (Files.exists(targetFile)) {
            //its already there lol
            return;
        }
        try {
            Files.createDirectories(targetDir);

            try (InputStream in = UpdateRPMod.class.getResourceAsStream("/UpdateRP.txt")) {
                if (in == null) {
                    LOGGER.error("Resource UpdateRP.txt not found in mod!");
                    return;
                }
                Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Copied UpdateRP.txt to FancyMenu config");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
