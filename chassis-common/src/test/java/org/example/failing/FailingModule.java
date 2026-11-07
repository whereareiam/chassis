package org.example.failing;

import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.common.Greeting;
import me.whereareiam.chassis.common.TestModule;
import me.whereareiam.chassis.common.Witness;

import java.lang.ref.WeakReference;
import me.whereareiam.chassis.service.ServiceKey;

public final class FailingModule implements TestModule {
	@Override
	public void enable(ExtensionContext context) {
		Witness.failingLoader = new WeakReference<>(getClass().getClassLoader());
		context.services().register(ServiceKey.of(Greeting.class, "failing"), () -> "never");
		context.onClose(() -> System.setProperty("chassis.test.failing.closed", "true"));
		throw new IllegalStateException("The failing module does not start");
	}
}
