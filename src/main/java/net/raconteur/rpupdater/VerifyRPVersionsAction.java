package net.raconteur.rpupdater;

import de.keksuccino.fancymenu.customization.action.Action;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.raconteur.rpupdater.config.ModConfigs;

import de.keksuccino.fancymenu.customization.variables.VariableHandler;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

public class VerifyRPVersionsAction extends Action {
    private static String cachedLatestVersion = null;
    private static long lastChecked = 0;
    private static final long CACHE_DURATION_MS = (1 * 60 * 1000)/2;


    public VerifyRPVersionsAction() {
        super("paul_verify_rp_versions");
    }


    @Override
    public boolean hasValue() {
        return false;
    }

    public static String getLocalRP() {
        Minecraft minecraft = Minecraft.getInstance();
        PackRepository pack_repo = minecraft.getResourcePackRepository();
        Collection<Pack> selected_packs = pack_repo.getSelectedPacks();
        Iterator<Pack> iterator = selected_packs.iterator();

        Pattern pattern = Pattern.compile(ModConfigs.RP_NAME_REGEX.get());
        while (iterator.hasNext()) {
            Pack pack = iterator.next();
            Matcher matcher = pattern.matcher(pack.getId());
            if (matcher.find()) {
                return matcher.group();
            }
        }
        return null;
    }

    private static String getLatestZipName(String latestReleaseUrl) {
        try {
            URL url = URI.create(latestReleaseUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setRequestProperty("User-Agent", "rpupdater-mod");

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder responseBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseBody.append(line);
            }
            reader.close();

            String jsonStr = responseBody.toString();

            JsonObject release = JsonParser.parseString(jsonStr).getAsJsonObject();

            JsonArray assets = release.getAsJsonArray("assets");
            if (assets == null || assets.size() == 0) {
                UpdateRPMod.LOGGER.error("No assets found in latest release.");
                return null;
            }
            JsonObject asset = assets.get(0).getAsJsonObject();
            if (!asset.has("name")) {
                UpdateRPMod.LOGGER.error("Asset has no 'name' field.");
                return null;
            }
            return asset.get("name").getAsString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

    }

    public static String getRPLatestRelease() {
        long now = System.currentTimeMillis();
        if (cachedLatestVersion != null && (now - lastChecked) < CACHE_DURATION_MS) {
            return cachedLatestVersion;
        }

        String latestReleaseUrl = String.format("https://api.github.com/repos/%s/%s/releases/latest", ModConfigs.REPO_OWNER.get(), ModConfigs.REPO_NAME.get());
        String latestZipName = getLatestZipName(latestReleaseUrl);

        if (latestZipName == null) {
            UpdateRPMod.LOGGER.error("Could not retrieve latest zip name");
            return null;
        }
        Pattern pattern = Pattern.compile(ModConfigs.RP_NAME_REGEX.get());
        Matcher matcher = pattern.matcher(latestZipName);
        if (!matcher.matches()) {
            UpdateRPMod.LOGGER.error("Zip name does not match expected pattern");
            return null;
        }
        cachedLatestVersion = latestZipName;
        lastChecked = now;
        return latestZipName;
    }

    @Override
    public void execute(@Nullable String value) {
        String local_version = getLocalRP();
        String latest_version = getRPLatestRelease();

        if (local_version != null && local_version.startsWith("file/")) {
            local_version = local_version.substring(local_version.indexOf("/") + 1);
        }

        //some of these null checks are unnecessary, should probably optimise
        if (local_version != null) {
            VariableHandler.setVariable("rp_actual_version", local_version);
        } else {
            VariableHandler.setVariable("rp_actual_version", "Null");
        }
        if (latest_version != null) {
            VariableHandler.setVariable("rp_latest_available_version", latest_version);
        }
        else {
            return;
        }
        if (local_version == null || !local_version.equals(latest_version)) {
            VariableHandler.setVariable("rp_need_update", "true");
        } else {
            VariableHandler.setVariable("rp_need_update", "false");
        }
    }

    @Override
    public @NotNull Component getActionDisplayName() {
        return Component.literal("verify_rp_versions");
    }

    @Override
    public Component[] getActionDescription() {
        return new Component[] { Component.literal("Verify versions and set corresponding variables: rp_latest_available_version, rp_actual_version, rp_need_update") };
    }

    @Override
    @Nullable
    public Component getValueDisplayName() {
        return null;
    }

    @Override
    @Nullable
    public String getValueExample() {
        return null;
    }
}
