package net.raconteur.rpupdater.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;

public class ModConfigs {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ConfigValue<String> RP_NAME_REGEX;
    public static final ConfigValue<String> REPO_OWNER;
    public static final ConfigValue<String> REPO_NAME;

    static {
        BUILDER.push("resourcepack-updater config");

        RP_NAME_REGEX = BUILDER.define("rp_name_regex", "<pack_name_regex>");
        REPO_OWNER = BUILDER.define("repo_owner", "<repo_owner>");
        REPO_NAME = BUILDER.define("repo_name", "<repo_name>");

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
