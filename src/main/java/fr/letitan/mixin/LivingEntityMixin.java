package fr.letitan.mixin;

import fr.letitan.LeTitan;
import fr.letitan.Rythme;
import fr.letitan.monde.Interieur;
import fr.letitan.vie.Accroche;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

	/**
	 * Dans les entrailles : le souffle de l'évent te projette dehors quand il expire, un courant
	 * d'air chaud remonte le puits de sortie, et le sang de la veine t'emporte vers la queue.
	 */
	@Inject(method = "travel", at = @At("HEAD"))
	private void letitan$entrailles(Vec3 input, CallbackInfo ci) {
		LivingEntity moi = (LivingEntity) (Object) this;
		if (!(moi instanceof Player) || moi.level().dimension() != LeTitan.DOS) {
			return;
		}
		Interieur.Organe organe = Interieur.organe(moi.getX(), moi.getY() + 0.5, moi.getZ());
		if (organe == null) {
			return;
		}
		Vec3 v = moi.getDeltaMovement();
		switch (organe) {
			case EVENT -> {
				if (Rythme.expire(moi.level().getGameTime()) && !moi.isShiftKeyDown()) {
					moi.setDeltaMovement(v.x, Math.max(v.y, 0.6), v.z);
				}
			}
			case SORTIE -> moi.setDeltaMovement(v.x, Math.max(v.y, 0.3), v.z);
			case VEINE -> {
				if (moi.isInWater()) {
					double[] c = Interieur.courant(moi.getX(), moi.getY(), moi.getZ());
					double vx = v.x + c[0] * 0.025, vz = v.z + c[1] * 0.025;
					double n = Math.hypot(vx, vz);
					if (n > 0.32) {
						vx *= 0.32 / n;
						vz *= 0.32 / n;
					}
					moi.setDeltaMovement(vx, v.y, vz);
				}
			}
			default -> {
			}
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
