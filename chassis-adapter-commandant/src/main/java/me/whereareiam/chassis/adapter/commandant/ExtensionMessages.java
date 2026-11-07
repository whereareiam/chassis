package me.whereareiam.chassis.adapter.commandant;

import lombok.Getter;
import lombok.Setter;

/**
 * What the commands of a host's extensions answer, in MiniMessage. A host keeps them in its messages file, so an
 * administrator words them. Each names what it may use, written as tags: {@code <kind>} (the host's word, such as
 * {@code module}), {@code <id>}, {@code <version>}, {@code <state>}, {@code <reason>}, {@code <count>}.
 */
@Getter
@Setter
@SuppressWarnings("FieldMayBeFinal")
public class ExtensionMessages {
	/**
	 * Above the list; {@code <count>}.
	 */
	private String listHeader = "<white><count> <kind>s:";
	/**
	 * One line of the list for each installed extension; {@code <id>}, {@code <version>}, {@code <state>}, {@code <reason>}.
	 */
	private String listLine = "  <yellow><id></yellow> <gray><version></gray> <state><reason>";
	private String listEmpty = "<gray>No <kind> is installed.";
	/**
	 * The detail of one; {@code <id>}, {@code <version>}, {@code <state>}, {@code <reason>}, and {@code <name>},
	 * {@code <description>}, {@code <authors>}, {@code <jar>}.
	 */
	private String info = "<yellow><name></yellow> <gray>(<id> <version>)</gray> <state><reason>\n<white><description>\n<gray>By <authors>, from <jar>";
	private String enabled = "<white>The <kind> <yellow><id></yellow> is on: <state><reason>";
	private String disabled = "<white>The <kind> <yellow><id></yellow> is off; it stays off across restarts.";
	private String reloaded = "<white>The <kind> <yellow><id></yellow> was loaded again from its jar: <state><reason>";
	/**
	 * After a rescan; {@code <count>} installed extensions.
	 */
	private String rescanned = "<white>Looked again: <count> <kind>s installed.";
	private String unknown = "<red>No <kind> <yellow><id></yellow> is installed.";
	/**
	 * When a change could not be made; {@code <reason>}.
	 */
	private String refused = "<red><reason>";
	/**
	 * The words for each state, in {@code <state>}.
	 */
	private String stateEnabled = "<green>running";
	private String stateDisabled = "<gray>turned off";
	private String stateWaiting = "<yellow>waiting";
	private String stateFailed = "<red>failed";
	/**
	 * How a waiting or failed one's reason follows its state, in {@code <reason>}; empty for a running one.
	 */
	private String reason = "<gray>: <why>";
}
