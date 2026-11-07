package org.example.dependent;

import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.common.Greeting;
import me.whereareiam.chassis.common.TestModule;
import me.whereareiam.chassis.service.ServiceKey;
import org.example.greeting.Shared;

public final class DependentModule implements TestModule {
	@Override
	public void enable(ExtensionContext context) {
		String greeting = context.services().find(ServiceKey.of(Greeting.class)).orElseThrow().text();
		if (!greeting.startsWith(Shared.GREETING)) throw new IllegalStateException("Unexpected greeting " + greeting);

		System.setProperty("chassis.test.dependent.saw", greeting);
		System.setProperty("chassis.test.dependent.junit", String.valueOf(visible("org.junit.jupiter.api.Test")));
	}

	private boolean visible(String name) {
		try {
			Class.forName(name, false, getClass().getClassLoader());
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}
}
