package com.accsaber.backend.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.color.ANSIConstants;
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase;

public class LevelColorConverter extends ForegroundCompositeConverterBase<ILoggingEvent> {

    @Override
    protected String getForegroundColorCode(ILoggingEvent event) {
        return switch (event.getLevel().toInt()) {
            case Level.ERROR_INT -> ANSIConstants.BOLD + ANSIConstants.RED_FG;
            case Level.WARN_INT -> ANSIConstants.YELLOW_FG;
            case Level.INFO_INT -> ANSIConstants.CYAN_FG;
            case Level.DEBUG_INT -> "90";
            default -> ANSIConstants.DEFAULT_FG;
        };
    }
}
