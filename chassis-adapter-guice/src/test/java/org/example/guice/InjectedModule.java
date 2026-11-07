package org.example.guice;

import jakarta.inject.Inject;
import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.adapter.guice.Clock;
import me.whereareiam.chassis.adapter.guice.GuiceModule;

public final class InjectedModule implements GuiceModule {
	private final Clock clock;
	private final Helper helper;
	private final ExtensionContext context;

	@Inject
	public InjectedModule(Clock clock, Helper helper, ExtensionContext context) {
		this.clock = clock;
		this.helper = helper;
		this.context = context;
	}

	@Override
	public void enable(ExtensionContext ignored) {
		System.setProperty("chassis.test.guice", context.descriptor().getId() + " " + clock.now() + " " + helper.greet());
	}

	/**
	 * A class of the extension that Guice makes just in time.
	 */
	public static final class Helper {
		public String greet() {
			return "hello";
		}
	}
}
