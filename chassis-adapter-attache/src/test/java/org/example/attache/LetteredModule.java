package org.example.attache;

import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.adapter.attache.AttacheModule;
import org.example.attache.library.Letters;

public final class LetteredModule implements AttacheModule {
	@Override
	public void enable(ExtensionContext context) {
		System.setProperty("chassis.test.attache", Letters.shout("hello"));
	}
}
