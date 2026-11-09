package org.example.library;

import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.common.TestModule;

public final class LibraryModule implements TestModule {
	@Override
	public void enable(ExtensionContext context) {
		System.setProperty("chassis.test.library.saw", LibraryText.TEXT);
	}
}
