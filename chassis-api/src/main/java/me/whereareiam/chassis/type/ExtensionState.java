package me.whereareiam.chassis.type;

/**
 * Where an installed extension stands.
 */
public enum ExtensionState {
	/**
	 * Running.
	 */
	ENABLED,
	/**
	 * Turned off by an administrator, through the host's selection; it stays off across restarts.
	 */
	DISABLED,
	/**
	 * Waiting for a dependency or a peer of the platform; it starts once what it waits for is there.
	 */
	WAITING,
	/**
	 * Its jar or descriptor could not be read, it does not fit the host's API, platform or offer, its dependencies form
	 * a cycle, or it failed to start. It starts again only when it is enabled or loaded again.
	 */
	FAILED
}
