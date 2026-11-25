package fr.letitan.mixin;

import fr.letitan.vie.Accroche;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Toucher une patte du Titan suffit : elle te hisse toute seule, tout droit, sans rien à
 * presser. S'accroupir fait redescendre le long de la patte.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
	private void letitan$agrippe(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && Accroche.agrippe((LivingEntity) (Object) this)) {
			cir.setReturnValue(true);
		}
	}

	/** Avant le déplacement : on monte (ou descend) le long de la patte, sans la traverser. */
	@Inject(method = "handleOnClimbable", at = @At("RETURN"), cancellable = true)
	private void letitan$hisser(Vec3 delta, CallbackInfoReturnable<Vec3> cir) {
		Double vitesse = letitan$vitesseSurLaPatte();
		if (vitesse != null) {
			cir.setReturnValue(letitan$mouvement(cir.getReturnValue(), vitesse));
		}
	}

	/** Après le déplacement : on garde cet élan pour le tick suivant. */
	@Inject(method = "handleRelativeFrictionAndCalculateMovement", at = @At("RETURN"), cancellable = true)
	private void letitan$garderLElan(Vec3 input, float friction, CallbackInfoReturnable<Vec3> cir) {
		Double vitesse = letitan$vitesseSurLaPatte();
		if (vitesse != null) {
			cir.setReturnValue(letitan$mouvement(cir.getReturnValue(), vitesse));
		}
	}

	/** Hissé, on monte tout droit ; accroupi, on garde la main pour s'éloigner de la patte. */
	private Vec3 letitan$mouvement(Vec3 mouvement, double vitesse) {
		LivingEntity moi = (LivingEntity) (Object) this;
		return moi.isShiftKeyDown() ? new Vec3(mouvement.x, vitesse, mouvement.z) : new Vec3(0, vitesse, 0);
	}

	private Double letitan$vitesseSurLaPatte() {
		LivingEntity moi = (LivingEntity) (Object) this;
		if (Accroche.aUnePatte(moi) == null) {
			return null;
		}
		return moi.isShiftKeyDown() ? -Accroche.VITESSE_DESCENTE : Accroche.VITESSE_MONTEE;
	}
}
