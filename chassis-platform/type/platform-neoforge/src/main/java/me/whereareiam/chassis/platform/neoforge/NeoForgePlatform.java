package me.whereareiam.chassis.platform.neoforge;

import me.whereareiam.chassis.model.Version;
import me.whereareiam.chassis.platform.Platform;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * NeoForge as Chassis sees it: named {@code neoforge}, its version is the game's, such as {@code 1.21.1}, and a
 * descriptor's {@code plugins} are mod ids. NeoForge loads
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
	public boolean hasPlugin(@NotNull String id) {
		ModList mods = ModList.get();

		return mods != null && mods.isLoaded(id);
	}
}
