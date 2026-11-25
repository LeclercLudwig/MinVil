package fr.letitan.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import fr.letitan.LeTitan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Passer du dos du Titan au monde d'en bas (ou l'inverse) se fait sans écran de chargement :
 * pour le joueur, c'est le même monde. Le jeu charge le nouveau monde pendant qu'on continue
 * de voir (et de tomber).
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Unique
	private boolean letitan$sansEcran;

	@Inject(method = "handleRespawn", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
			shift = At.Shift.AFTER))
	private void letitan$avantChangement(ClientboundRespawnPacket paquet, CallbackInfo ci) {
		var joueur = Minecraft.getInstance().player;
		if (joueur == null) {
			letitan$sansEcran = false;
			return;
		}
		ResourceKey<Level> avant = joueur.level().dimension();
		ResourceKey<Level> apres = paquet.commonPlayerSpawnInfo().dimension();
		letitan$sansEcran = !joueur.isDeadOrDying() && avant != apres && letitan$titanOuSol(avant) && letitan$titanOuSol(apres);
	}

	@Unique
	private static boolean letitan$titanOuSol(ResourceKey<Level> monde) {
		return monde == LeTitan.DOS || monde == Level.OVERWORLD;
	}

	@WrapWithCondition(method = "startWaitingForNewLevel", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/Minecraft;setScreenAndShow(Lnet/minecraft/client/gui/screens/Screen;)V"))
	private boolean letitan$ecranDeChargement(Minecraft minecraft, Screen ecran) {
		boolean montrer = !letitan$sansEcran;
		letitan$sansEcran = false;
		return montrer;
	}
}
