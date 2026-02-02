package com.i113w.attributefixsync;

import com.i113w.attributefixsync.mixin.RangedAttributeAccessor;
import com.i113w.attributefixsync.network.SyncAttributeLimitsPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod(AttributeFixSync.MOD_ID)
public class AttributeFixSync {

    public static final String MOD_ID = "attributefixsync";
    public static final String MOD_VERSION = "0.0.2";

    private static final Logger LOGGER = LoggerFactory.getLogger("AttributeFixSync");

    // 服务端缓存：服务器启动时构建，避免每次玩家登录重复扫描
    private static Map<ResourceLocation, Double> SERVER_CACHE = null;

    // 客户端备份：使用线程安全的 Map，防止并发问题
    private static final Map<ResourceLocation, Double> CLIENT_ORIGINAL_MAX_VALUES = new ConcurrentHashMap<>();

    public AttributeFixSync(IEventBus modEventBus, ModContainer modContainer) {
        // 注册配置文件
        modContainer.registerConfig(ModConfig.Type.CLIENT, AttributeFixSyncConfig.CLIENT_SPEC);

        modEventBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.register(this);
    }

    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                SyncAttributeLimitsPayload.TYPE,
                SyncAttributeLimitsPayload.CODEC,
                this::handleDataOnClient
        );
    }


     // 服务器启动时构建缓存
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        SERVER_CACHE = new HashMap<>();
        int count = 0;
        for (Attribute attr : BuiltInRegistries.ATTRIBUTE) {
            if (attr instanceof RangedAttribute rangedAttr) {
                ResourceLocation id = BuiltInRegistries.ATTRIBUTE.getKey(attr);
                if (id != null) {
                    // 存储当前服务端生效的上限值（已被 AttributeFix 修改过的）
                    SERVER_CACHE.put(id, rangedAttr.getMaxValue());
                    count++;
                }
            }
        }
        LOGGER.info("Server cached {} attribute limits for synchronization.", count);
    }


     // 玩家登录时直接发送缓存数据

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            // 如果缓存为空（极少情况，如热重载），临时构建一次
            if (SERVER_CACHE == null) {
                onServerStarting(null);
            }

            serverPlayer.connection.send(new SyncAttributeLimitsPayload(MOD_VERSION, SERVER_CACHE));
        }
    }

    private void handleDataOnClient(final SyncAttributeLimitsPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            // 检查配置是否允许同步
            if (!AttributeFixSyncConfig.CLIENT.enableSync.get()) {
                LOGGER.info("Attribute synchronization disabled by config.");
                return;
            }

            // 简单版本警告（仅打印日志，不阻止运行，防止小版本更新导致无法兼容）
            if (!MOD_VERSION.equals(payload.modVersion())) {
                LOGGER.warn("AttributeFixSync version mismatch! Client: {}, Server: {}. Proceeding anyway.", MOD_VERSION, payload.modVersion());
            }

            // 1. 备份逻辑：仅在首次连接时备份，防止多次备份覆盖为修改后的值
            if (CLIENT_ORIGINAL_MAX_VALUES.isEmpty()) {
                for (Attribute attr : BuiltInRegistries.ATTRIBUTE) {
                    if (attr instanceof RangedAttribute rangedAttr) {
                        ResourceLocation id = BuiltInRegistries.ATTRIBUTE.getKey(attr);
                        if (id != null) {
                            CLIENT_ORIGINAL_MAX_VALUES.put(id, rangedAttr.getMaxValue());
                        }
                    }
                }
            }

            // 2. 应用逻辑：包含空检查和异常处理
            Map<ResourceLocation, Double> serverLimits = payload.limits();
            int successCount = 0;
            int failCount = 0;

            for (Map.Entry<ResourceLocation, Double> entry : serverLimits.entrySet()) {
                ResourceLocation id = entry.getKey();
                double serverMax = entry.getValue();

                try {
                    // 3: 严格的空检查
                    if (!BuiltInRegistries.ATTRIBUTE.containsKey(id)) {
                        continue; // 客户端缺少该属性（Mod不匹配），跳过
                    }

                    Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(id);
                    if (attribute instanceof RangedAttribute rangedAttr) {
                        // 仅当数值不同步时修改
                        if (Double.compare(rangedAttr.getMaxValue(), serverMax) != 0) {
                            ((RangedAttributeAccessor) rangedAttr).afsync$setMaxValue(serverMax);
                            successCount++;
                        }
                    }
                } catch (Exception e) {
                    // 4: 异常捕获，防止崩端
                    failCount++;
                    LOGGER.error("Failed to sync attribute limit for {}", id, e);
                }
            }

            LOGGER.info("Synced {} attributes from server. ({} failed)", successCount, failCount);

            // 5: 用户反馈
            if (successCount > 0 && AttributeFixSyncConfig.CLIENT.showSyncMessages.get()) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("[AttributeFixSync] ")
                                .withStyle(ChatFormatting.GREEN)
                                .append(Component.literal("Synchronized " + successCount + " attribute limits from server.")
                                        .withStyle(ChatFormatting.WHITE)),
                        false
                );
            }
        });
    }

    @SubscribeEvent
    public void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (!CLIENT_ORIGINAL_MAX_VALUES.isEmpty()) {
            LOGGER.info("Restoring {} original attribute limits...", CLIENT_ORIGINAL_MAX_VALUES.size());

            int restoredCount = 0;
            for (Map.Entry<ResourceLocation, Double> entry : CLIENT_ORIGINAL_MAX_VALUES.entrySet()) {
                try {
                    ResourceLocation id = entry.getKey();
                    double originalMax = entry.getValue();

                    if (BuiltInRegistries.ATTRIBUTE.containsKey(id)) {
                        Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(id);
                        if (attribute instanceof RangedAttribute rangedAttr) {
                            ((RangedAttributeAccessor) rangedAttr).afsync$setMaxValue(originalMax);
                            restoredCount++;
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to restore attribute {}", entry.getKey(), e);
                }
            }

            // 清空备份，确保下次进服时能重新建立正确的基准备份
            CLIENT_ORIGINAL_MAX_VALUES.clear();
            LOGGER.info("Restored {} attributes.", restoredCount);
        }
    }
}