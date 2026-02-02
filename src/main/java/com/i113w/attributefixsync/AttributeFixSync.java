package com.i113w.attributefixsync;

import com.i113w.attributefixsync.mixin.RangedAttributeAccessor;
import com.i113w.attributefixsync.network.SyncAttributeLimitsPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Mod("attributefixsync")
public class AttributeFixSync {

    public static final String MOD_ID = "attributefixsync";
    private static final String MOD_NAME = "AttributeFixSync"; // 先定义名字
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME); // 再使用名字

    // 客户端备份：用于在退出服务器时还原属性，防止污染单人游戏
    // Key: 属性ID, Value: 原始的上限值
    private static final Map<ResourceLocation, Double> CLIENT_ORIGINAL_MAX_VALUES = new HashMap<>();

    public AttributeFixSync(IEventBus modEventBus) {
        // 在 Mod 总线上注册网络包注册事件
        modEventBus.addListener(this::registerPayloads);

        // 在 NeoForge 总线上注册游戏事件 (登录、登出)
        NeoForge.EVENT_BUS.register(this);
    }

    // 1. 注册网络包
    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1"); // 版本号 "1"
        registrar.playToClient(
                SyncAttributeLimitsPayload.TYPE,
                SyncAttributeLimitsPayload.CODEC,
                this::handleDataOnClient
        );
    }

    // 2. 服务端逻辑：玩家登录时发送数据
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        // 只在服务端执行
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            Map<ResourceLocation, Double> limits = new HashMap<>();

            // 扫描游戏内所有注册的属性
            for (Attribute attr : BuiltInRegistries.ATTRIBUTE) {
                // 只关心 RangedAttribute (有数值范围的属性)
                if (attr instanceof RangedAttribute rangedAttr) {
                    ResourceLocation id = BuiltInRegistries.ATTRIBUTE.getKey(attr);
                    // 获取当前服务端的上限值
                    // 如果服务端安装了 AttributeFix，这个值已经是被修改过的（例如 1,000,000）
                    limits.put(id, rangedAttr.getMaxValue());
                }
            }

            // 发送数据包给登录的玩家
            serverPlayer.connection.send(new SyncAttributeLimitsPayload(limits));
            LOGGER.info("Sent attribute sync packet with {} entries to {}", limits.size(), serverPlayer.getName().getString());
        }
    }

    // 3. 客户端逻辑：接收并应用数据
    private void handleDataOnClient(final SyncAttributeLimitsPayload payload, final IPayloadContext context) {
        // 确保在主线程执行
        context.enqueueWork(() -> {
            // A. 如果是第一次接收，先备份客户端原始值
            if (CLIENT_ORIGINAL_MAX_VALUES.isEmpty()) {
                for (Attribute attr : BuiltInRegistries.ATTRIBUTE) {
                    if (attr instanceof RangedAttribute rangedAttr) {
                        CLIENT_ORIGINAL_MAX_VALUES.put(
                                BuiltInRegistries.ATTRIBUTE.getKey(attr),
                                rangedAttr.getMaxValue()
                        );
                    }
                }
            }

            // B. 应用服务端发来的新限制
            Map<ResourceLocation, Double> serverLimits = payload.limits();
            int modifiedCount = 0;

            for (Map.Entry<ResourceLocation, Double> entry : serverLimits.entrySet()) {
                ResourceLocation id = entry.getKey();
                double serverMax = entry.getValue();

                Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(id);
                if (attribute instanceof RangedAttribute rangedAttr) {
                    // 只有当数值不同时才进行修改
                    if (Double.compare(rangedAttr.getMaxValue(), serverMax) != 0) {
                        // 使用 Mixin Accessor 强行修改 private 字段
                        ((RangedAttributeAccessor) rangedAttr).afsync$setMaxValue(serverMax);
                        modifiedCount++;
                    }
                }
            }
            LOGGER.info("Synced {} attributes from server.", modifiedCount);
        });
    }

    // 4. 客户端逻辑：退出服务器/断开连接时还原
    @SubscribeEvent
    public void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        // 如果有备份，说明之前修改过
        if (!CLIENT_ORIGINAL_MAX_VALUES.isEmpty()) {
            LOGGER.info("Restoring {} original attribute limits...", CLIENT_ORIGINAL_MAX_VALUES.size());

            for (Map.Entry<ResourceLocation, Double> entry : CLIENT_ORIGINAL_MAX_VALUES.entrySet()) {
                ResourceLocation id = entry.getKey();
                double originalMax = entry.getValue();

                Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(id);
                if (attribute instanceof RangedAttribute rangedAttr) {
                    // 还原回原始值
                    ((RangedAttributeAccessor) rangedAttr).afsync$setMaxValue(originalMax);
                }
            }

            // 清空备份，防止下次进服逻辑出错
            CLIENT_ORIGINAL_MAX_VALUES.clear();
        }
    }
}