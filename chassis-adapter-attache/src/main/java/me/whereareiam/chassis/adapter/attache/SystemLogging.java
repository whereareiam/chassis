package me.whereareiam.chassis.adapter.attache;

import lombok.RequiredArgsConstructor;
import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.type.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Attache's log lines on a {@link System.Logger}.
 */
@RequiredArgsConstructor
final class SystemLogging implements LoggingHelper {
	private final @NotNull System.Logger logger;

	@Override
	public void log(@NotNull Level level, @NotNull String message) {
		logger.log(level(level), message);
	}

	@Override
	public void log(@NotNull Level level, @NotNull String message, @NotNull Throwable throwable) {
		logger.log(level(level), message, throwable);
	}

	private static System.Logger.Level level(Level level) {
		return switch (level) {
			case DEBUG -> System.Logger.Level.DEBUG;
			case INFO -> System.Logger.Level.INFO;
			case WARN -> System.Logger.Level.WARNING;
			case ERROR -> System.Logger.Level.ERROR;
		};
	}
}
