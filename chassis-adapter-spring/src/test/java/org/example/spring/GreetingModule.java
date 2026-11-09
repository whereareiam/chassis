package org.example.spring;

import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.adapter.spring.HostPlugin;
import me.whereareiam.chassis.adapter.spring.SpringModule;
import org.example.spring.api.Words;

@HostPlugin
public class GreetingModule implements SpringModule {
	private final Words words;

	public GreetingModule(Words words) {
		this.words = words;
	}

	@Override
	public void enable(ExtensionContext context) {
		System.setProperty("chassis.test.spring.greeting", words.greeting());
	}
}
