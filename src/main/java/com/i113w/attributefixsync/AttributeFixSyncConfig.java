package com.i113w.attributefixsync;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class AttributeFixSyncConfig {
    public static final ModConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        final Pair<Client, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT_SPEC = specPair.getRight();
        CLIENT = specPair.getLeft();
    }

    public static class Client {
        public final ModConfigSpec.BooleanValue enableSync;
        public final ModConfigSpec.BooleanValue showSyncMessages;

        public Client(ModConfigSpec.Builder builder) {
            builder.push("general");

            enableSync = builder
                    .comment("Whether to accept attribute limit synchronization from the server.")
                    .define("enableSync", true);

            showSyncMessages = builder
                    .comment("Whether to show a chat message when attributes are synchronized.")
                    .define("showSyncMessages", true);

            builder.pop();
        }
    }
}