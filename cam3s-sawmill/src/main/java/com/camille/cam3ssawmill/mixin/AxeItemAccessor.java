package com.camille.cam3ssawmill.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.item.AxeItem;
import net.minecraft.world.level.block.Block;

/** Read access to the axe stripping table (log -> stripped log), which includes modded logs registered through Fabric API. */
@Mixin(AxeItem.class)
public interface AxeItemAccessor {
	@Accessor("STRIPPABLES")
	static Map<Block, Block> cam3sSawmill$getStrippables() {
		throw new AssertionError("Untransformed @Accessor");
	}
}
