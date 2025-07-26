package net.raconteur.rpupdater;

import de.keksuccino.fancymenu.customization.action.Action;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;
import net.raconteur.rpupdater.config.ModConfigs;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;


public class UpdateRPAction extends Action {
    public UpdateRPAction() {
        //The action identifier needs to be unique, so just use your username or something similar as prefix
        super("paul_update_rp");
    }

    //If the custom action has a value or not
    @Override
    public boolean hasValue() {
        return false;
    }

    // Get latest release name
    private static String getLatestZipUrl(String latestReleaseUrl) {
        try {
            URL url = URI.create(latestReleaseUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setRequestProperty("User-Agent", "rpupdater-mod");

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            JsonObject release = JsonParser.parseReader(reader).getAsJsonObject();
            reader.close();

            JsonArray assets = release.getAsJsonArray("assets");
            JsonObject asset = assets.get(0).getAsJsonObject();
            return asset.get("browser_download_url").getAsString();
        } catch (IOException e) {
            UpdateRPMod.LOGGER.error("Failed to get URL!");
            e.printStackTrace();
            return null;
        }
    }

    private static boolean downloadZip(String zipUrl, String outputPath) {
        String localVersion = zipUrl.substring(zipUrl.lastIndexOf('/')+1);
        File file = new File(outputPath, localVersion);
        if (file.exists()) {
            UpdateRPMod.LOGGER.info("Latest version already found on client, attempting to enable...");
            return true;
        }
        else {
            try {
                URL url = URI.create(zipUrl).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
                connection.setRequestProperty("User-Agent", "rpupdater-mod");

                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    UpdateRPMod.LOGGER.error("Failed to download zip: {}", responseCode);
                    return false;
                }

                String fileName = zipUrl.substring(zipUrl.lastIndexOf("/") + 1);
                File outputFile = new File(outputPath, fileName);

                InputStream inputStream = connection.getInputStream();
                OutputStream outputStream = new FileOutputStream(outputFile);
                byte[] buffer = new byte[4096];
                int bytesRead = -1;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.close();
                inputStream.close();

                return true;
            } catch (IOException e) {
                UpdateRPMod.LOGGER.error("Failed to download zip ! Exception raised.");
                e.printStackTrace();
                return false;
            }
        }
    }

    //Download latest resource pack
    private String downloadPack() {
        String owner = ModConfigs.REPO_OWNER.get();
        String repo = ModConfigs.REPO_NAME.get();
        String latestReleaseUrl = String.format("https://api.github.com/repos/%s/%s/releases/latest", owner, repo);
        String latestZipUrl = getLatestZipUrl(latestReleaseUrl);
        if (latestZipUrl == null) {
            UpdateRPMod.LOGGER.error("Could not retrieve latest zip URL");
            return null;
        }

        Pattern pattern = Pattern.compile(ModConfigs.RP_NAME_REGEX.get());
        Matcher matcher = pattern.matcher(latestZipUrl);
        if (!matcher.find()) {
            UpdateRPMod.LOGGER.error("Zip URL does not match expected pattern");
            return null;
        }

        String resourcepacksPath = "./resourcepacks";
        if (downloadZip(latestZipUrl, resourcepacksPath)) {
            UpdateRPMod.LOGGER.info("Successfully downloaded {} to {}", latestZipUrl, resourcepacksPath);
        } else {
            UpdateRPMod.LOGGER.error("Failed to download {} to {}", latestZipUrl, resourcepacksPath);
        }
        return matcher.group();
    }

    //Grabs the old file name for deletion later
    public static @Nullable String getOldPackFilename(String regexPattern) {
        Pattern regex = Pattern.compile(regexPattern);
        for (String id : Minecraft.getInstance().options.resourcePacks) {
            if (id.startsWith("file/")) {
                String filename = id.substring("file/".length());
                Matcher matcher = regex.matcher(filename);
                if (matcher.find()) {
                    return filename;
                }
            }
        }
        return null;
    }

    //Attempts to delete the old pack
    public static void tryDeleteOldPack(String oldPackName) {
        if (oldPackName != null) {
            File oldFile = new File("resourcepacks", oldPackName);
            if (oldFile.exists() && oldFile.delete()) {
                UpdateRPMod.LOGGER.info("Deleted old pack zip: {}", oldPackName);
            } else {
                UpdateRPMod.LOGGER.warn("Failed to delete old pack: {} (full path: {})", oldPackName, oldFile.getAbsolutePath());
            }
        } else {
            UpdateRPMod.LOGGER.info("No matching old pack found for deletion. Has the file format changed?");
        }
    }


    //Set selected resource pack
    private void setSelected(String updatedPackName, String fileNameRegex, String oldPackName) {
        Minecraft minecraft = Minecraft.getInstance();
        PackRepository repository = minecraft.getResourcePackRepository();

        repository.reload();

        //extremely lazy way of stripping the name from the url
        updatedPackName = updatedPackName.substring(updatedPackName.lastIndexOf('/') + 1);

        //check if it exists and sets the name correctly
        String targetId = "file/" + updatedPackName;
        boolean exists = repository.getAvailablePacks().stream()
                .anyMatch(pack -> pack.getId().equals(targetId));
        if (!exists) {
            UpdateRPMod.LOGGER.error("Pack not found: {}", updatedPackName);
            return;
        }
        Pattern regex = Pattern.compile(fileNameRegex);


        List<String> selected = new ArrayList<>(minecraft.options.resourcePacks);

        selected.removeIf(id -> regex.matcher(id).matches());



        selected.add(0, targetId);

        minecraft.options.resourcePacks = selected;
        repository.setSelected(selected);
        minecraft.options.save();
        minecraft.reloadResourcePacks();

        UpdateRPMod.LOGGER.info("Activated resource pack: {}", updatedPackName);

        tryDeleteOldPack(oldPackName);

    }

    //gets called when button with action is clicked
    @Override
    public void execute(@Nullable String value) {
        String regexPattern = ModConfigs.RP_NAME_REGEX.get();
        String oldPackName = getOldPackFilename(regexPattern);
        String packName = downloadPack();

        if (packName != null) {
            setSelected(packName, ModConfigs.RP_NAME_REGEX.get(), oldPackName);
        }
    }

    @Override
    public @NotNull Component getActionDisplayName() {
        return Component.literal("update_rp");
    }

    @Override
    public Component[] getActionDescription() {
        return new Component[] { Component.literal("Update the resource pack.") };
    }

    @Override
    public Component getValueDisplayName() {
        return null;
    }


    //??
    @Override
    public String getValueExample() {
        return null;
    }
}
