package dev.monthendbuddy.common.persistence;

import java.util.UUID;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import dev.monthendbuddy.common.UserId;

/** Stores {@link UserId} as a {@code uuid} column wherever it appears. */
@Converter(autoApply = true)
public class UserIdConverter implements AttributeConverter<UserId, UUID> {

	@Override
	public UUID convertToDatabaseColumn(UserId attribute) {
		return attribute == null ? null : attribute.value();
	}

	@Override
	public UserId convertToEntityAttribute(UUID dbData) {
		return dbData == null ? null : new UserId(dbData);
	}

}
