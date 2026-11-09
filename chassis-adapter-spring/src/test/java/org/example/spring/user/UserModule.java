package org.example.spring.user;

import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.adapter.spring.SpringModule;
import org.example.spring.api.Words;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserModule implements SpringModule {
	private final Words words;
	private final ObjectProvider<Object> everything;

	public UserModule(Words words, ObjectProvider<Object> everything) {
		this.words = words;
		this.everything = everything;
	}

	@Override
	public void enable(ExtensionContext context) {
		boolean hidden = everything.stream().anyMatch(bean -> bean.getClass().getSimpleName().equals("Hidden"));
		System.setProperty("chassis.test.spring.user", words.greeting() + " " + hidden);
	}
}
