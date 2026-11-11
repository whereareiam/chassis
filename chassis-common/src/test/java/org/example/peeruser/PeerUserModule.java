package org.example.peeruser;

import me.whereareiam.chassis.ExtensionContext;
import me.whereareiam.chassis.common.TestModule;
import org.example.peer.PeerApi;
import org.jetbrains.annotations.NotNull;

public final class PeerUserModule implements TestModule {
	@Override
	public void enable(@NotNull ExtensionContext context) {
		System.setProperty("chassis.test.peeruser.saw", PeerApi.NAME + " from " + PeerApi.class.getClassLoader().getName());
		System.setProperty("chassis.test.peeruser.internal", String.valueOf(visible("org.example.peerhidden.PeerInternal")));
	}

	private boolean visible(String name) {
		try {
			Class.forName(name, false, getClass().getClassLoader());
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}
}
