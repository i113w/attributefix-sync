package com.i113w.attributefixsync.mixin;

import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RangedAttribute.class)
public interface RangedAttributeAccessor {

    // 生成一个 setMaxValue 方法，允许我们修改 private final double maxValue
    @Accessor("maxValue")
    @Mutable
    void afsync$setMaxValue(double maxValue);
}