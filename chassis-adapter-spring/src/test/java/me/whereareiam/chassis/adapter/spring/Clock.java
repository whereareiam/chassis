package me.whereareiam.chassis.adapter.spring;

/**
 * A bean of the host's context, of a type the host exports.
 */
public interface Clock {
	int now();
}
