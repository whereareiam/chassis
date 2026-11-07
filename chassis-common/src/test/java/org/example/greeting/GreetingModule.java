package org.example.greeting;

import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.common.Greeting;
import me.whereareiam.chassis.common.TestModule;
import me.whereareiam.chassis.service.ServiceKey;

public final class GreetingModule implements TestModule {
	// Counts the enables of one class: a fresh class loader starts it at zero.
	public static int enables;

	@Override
	public void enable(ExtensionContext context) {
		enables++;
		context.services().register(ServiceKey.of(Greeting.class), () -> Shared.GREETING + " " + enables);
		context.onClose(() -> System.setProperty("chassis.test.greeting.closed", "true"));
	}
}
