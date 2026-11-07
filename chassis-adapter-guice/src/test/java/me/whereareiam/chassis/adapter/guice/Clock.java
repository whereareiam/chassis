package me.whereareiam.chassis.adapter.guice;

/**
 * A host service the tests export to extensions.
 */
public interface Clock {
	long now();
}
