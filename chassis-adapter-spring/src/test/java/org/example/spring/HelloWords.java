package org.example.spring;

import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.adapter.spring.Clock;
import org.example.spring.api.Words;
import org.springframework.stereotype.Component;

@Component
public class HelloWords implements Words {
	private final ExtensionContext context;
	private final Clock clock;

	public HelloWords(ExtensionContext context, Clock clock) {
		this.context = context;
		this.clock = clock;
	}

	@Override
	public String greeting() {
		return "hello from " + context.descriptor().getId() + " at " + clock.now();
	}
}
