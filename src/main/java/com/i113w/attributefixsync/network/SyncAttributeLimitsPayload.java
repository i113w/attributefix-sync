package com.i113w.attributefixsync.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

// 使用 Java Record 定义数据包结构：一个 Map，键是属性ID，值是上限数值
public record SyncAttributeLimitsPayload(Map<ResourceLocation, Double> limits) implements CustomPacketPayload {

    // 定义数据包的唯一 ID
    public static final CustomPacketPayload.Type<SyncAttributeLimitsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("attributefixsync", "sync_limits"));

    // 定义编解码器，告诉游戏如何把这个 Map 写入网络流
    // 使用 ByteBufCodecs.map 自动处理 Map 的序列化
    public static final StreamCodec<ByteBuf, SyncAttributeLimitsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.map(
                    HashMap::new,                   // Map 实现工厂
                    ResourceLocation.STREAM_CODEC,  // Key 的编解码器 (ResourceLocation)
                    ByteBufCodecs.DOUBLE            // Value 的编解码器 (Double)
            ),
            SyncAttributeLimitsPayload::limits,     // Getter
            SyncAttributeLimitsPayload::new         // Constructor
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}