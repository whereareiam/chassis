package me.whereareiam.chassis.platform.neoforge;

import me.whereareiam.chassis.model.Version;
import me.whereareiam.chassis.platform.Platform;
import me.whereareiam.chassis.platform.PlatformPeer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * NeoForge as Chassis sees it: named {@code neoforge}, its version is the game's, such as {@code 1.21.1}, and a
 * descriptor's {@code peers} are mod ids. NeoForge loads
 * every mod before the server starts, so an extension never waits for a mod that comes later.
 *
 * <p>Every mod's classes are in one loader on NeoForge, so a host exports another mod's API to its extensions by naming
 * that API's packages in its export layer, over its own loader.</p>
 */
public final class NeoForgePlatform implements Platform {
	@Override
	public @NotNull String name() {
		return "neoforge";
	}

	@Override
	public @Nullable Version version() {
		try {
			return Version.parse(FMLLoader.versionInfo().mcVersion());
		} catch (RuntimeException unknown) {
			return null;
		}
	}

	@Override
	public @NotNull Optional<PlatformPeer> peer(@NotNull String id) {
		ModList mods = ModList.get();
		if (mods == null) return Optional.empty();

		// Every mod's classes are in the host's own loader, so a mod brings no loader of its own.
		return mods.getModContainerById(id).map(mod -> PlatformPeer.builder()
				.id(mod.getModId())
				.version(mod.getModInfo().getVersion().toString())
				.build());
	}
}
