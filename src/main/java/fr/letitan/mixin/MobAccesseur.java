package fr.letitan.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Pour ôter aux Muets leur façon de viser à vue : ils ne chassent qu'au bruit. */
@Mixin(Mob.class)
public interface MobAccesseur {
	@Accessor("targetSelector")
	GoalSelector letitan$selecteurDeCibles();
}
