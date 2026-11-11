package org.example.library;

import me.whereareiam.chassis.adapter.attache.AttacheModule;
import me.whereareiam.chassis.ExtensionContext;
import org.jetbrains.annotations.NotNull;

public final class LibraryModule implements AttacheModule {
	@Override
	public void enable(@NotNull ExtensionContext context) throws Exception {
		ClassLoader loader = getClass().getClassLoader();
		System.setProperty("chassis.test.library.own", String.valueOf(present(loader, "dev.example.greeting.Greeting")));
		System.setProperty("chassis.test.library.driver", String.valueOf(present(loader, "dev.example.driver.Driver")));
		context.libraries().loadGroups("sqlite");
		System.setProperty("chassis.test.library.group", String.valueOf(present(loader, "dev.example.driver.Driver")));
	}

	private static boolean present(ClassLoader loader, String name) {
		try {
			Class.forName(name, false, loader);

			return true;
		} catch (ClassNotFoundException absent) {
			return false;
		}
	}
}
