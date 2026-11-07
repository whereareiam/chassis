package me.whereareiam.chassis.service;

/**
 * A registered service; closing it removes the service. The host also removes it when its owner stops.
 */
public interface ServiceRegistration extends AutoCloseable {
	/**
	 * Removes the service. Closing it again does nothing.
	 */
	@Override
	void close();
}
