package org.example.attache;

import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.adapter.attache.AttacheModule;
import me.whereareiam.chassis.adapter.attache.GroupedContext;

public final class GroupedModule implements AttacheModule {
	@Override
	public void enable(ExtensionContext context) {
		ClassLoader loader = getClass().getClassLoader();
		System.setProperty("chassis.test.attache.before", String.valueOf(present(loader)));
		((GroupedContext) context).groups().load("sqlite");
		System.setProperty("chassis.test.attache.after", String.valueOf(present(loader)));
	}

	private static boolean present(ClassLoader loader) {
		try {
			Class.forName("org.example.attache.driver.Driver", false, loader);

			return true;
		} catch (ClassNotFoundException absent) {
			return false;
		}
	}
}
