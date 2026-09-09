package com.skilltracker.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Maps {@link MemberStatus} onto the varchar literals stored in {@code users.member_status}. */
@Converter
public class MemberStatusConverter implements AttributeConverter<MemberStatus, String> {

    @Override
    public String convertToDatabaseColumn(MemberStatus attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public MemberStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : MemberStatus.fromValue(dbData);
    }
}
