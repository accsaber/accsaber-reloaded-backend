package com.accsaber.backend.config;

import java.math.BigDecimal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

@Configuration
public class PlainDoubleJacksonConfig {

    private static final double MAX_EXACT_INTEGRAL = 9007199254740992.0;

    static boolean isExactIntegral(double d) {
        return d == Math.rint(d) && Math.abs(d) <= MAX_EXACT_INTEGRAL;
    }

    static String expandedExponent(double d) {
        String rendered = Double.toString(d);
        return rendered.indexOf('E') < 0 ? null : new BigDecimal(rendered).stripTrailingZeros().toPlainString();
    }

    public static final class PlainDoubleSerializer extends ValueSerializer<Double> {

        @Override
        public void serialize(Double value, JsonGenerator gen, SerializationContext ctxt) {
            if (value == null) {
                gen.writeNull();
                return;
            }
            double d = value;
            if (isExactIntegral(d)) {
                gen.writeNumber((long) d);
                return;
            }
            String expanded = expandedExponent(d);
            if (expanded == null) {
                gen.writeNumber(d);
            } else {
                gen.writeRawValue(expanded);
            }
        }
    }

    @Bean
    JacksonModule plainDoubleModule() {
        SimpleModule module = new SimpleModule("PlainDoubleModule");
        PlainDoubleSerializer serializer = new PlainDoubleSerializer();
        module.addSerializer(Double.class, serializer);
        module.addSerializer(Double.TYPE, serializer);
        return module;
    }
}
