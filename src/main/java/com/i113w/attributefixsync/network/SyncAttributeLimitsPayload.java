package com.i113w.attributefixsync.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public record SyncAttributeLimitsPayload(String modVersion, Map<ResourceLocation, Double> limits) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncAttributeLimitsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("attributefixsync", "sync_limits"));

    public static final StreamCodec<ByteBuf, SyncAttributeLimitsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,              // 版本号
            SyncAttributeLimitsPayload::modVersion,
            ByteBufCodecs.map(
                    HashMap::new,
                    ResourceLocation.STREAM_CODEC,
                    ByteBufCodecs.DOUBLE
            ),
            SyncAttributeLimitsPayload::limits,
            SyncAttributeLimitsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}