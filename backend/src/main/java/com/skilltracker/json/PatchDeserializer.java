package com.skilltracker.json;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;

/**
 * Deserializes {@link Patch} values, mapping a missing property to {@link Patch#absent()} and an
 * explicit JSON null to a present null.
 */
public class PatchDeserializer extends ValueDeserializer<Patch<?>> {

    private final JavaType contentType;

    public PatchDeserializer() {
        this(null);
    }

    private PatchDeserializer(JavaType contentType) {
        this.contentType = contentType;
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext context, BeanProperty property) {
        JavaType wrapperType = property != null ? property.getType() : context.getContextualType();
        JavaType resolvedContentType =
                wrapperType != null && wrapperType.containedTypeCount() > 0 ? wrapperType.containedType(0) : null;
        return new PatchDeserializer(resolvedContentType);
    }

    @Override
    public Patch<?> deserialize(JsonParser parser, DeserializationContext context) {
        if (contentType == null) {
            return Patch.of(context.readValue(parser, Object.class));
        }
        return Patch.of(context.readValue(parser, contentType));
    }

    @Override
    public Object getNullValue(DeserializationContext context) {
        return Patch.of(null);
    }

    @Override
    public Object getAbsentValue(DeserializationContext context) {
        return Patch.absent();
    }
}
