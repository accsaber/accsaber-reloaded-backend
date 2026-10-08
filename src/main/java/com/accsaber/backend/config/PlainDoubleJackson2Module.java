package com.accsaber.backend.config;

import java.io.IOException;
import java.math.BigDecimal;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class PlainDoubleJackson2Module {

    private PlainDoubleJackson2Module() {
    }

    private static final class PlainDoubleSerializer extends JsonSerializer<Double> {

        @Override
        public void serialize(Double value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            double d = value;
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                gen.writeNumber(d);
                return;
            }
            if (d == Math.rint(d) && Math.abs(d) <= PlainDoubleJacksonConfig.MAX_EXACT_INTEGRAL) {
                gen.writeNumber((long) d);
                return;
            }
            String rendered = Double.toString(d);
            if (rendered.indexOf('E') < 0) {
                gen.writeNumber(d);
                return;
            }
            gen.writeRawValue(new BigDecimal(rendered).stripTrailingZeros().toPlainString());
        }
    }

    public static SimpleModule create() {
        SimpleModule module = new SimpleModule("PlainDoubleModule");
        PlainDoubleSerializer serializer = new PlainDoubleSerializer();
        module.addSerializer(Double.class, serializer);
        module.addSerializer(Double.TYPE, serializer);
        return module;
    }

    public static ObjectMapper mapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(create())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
