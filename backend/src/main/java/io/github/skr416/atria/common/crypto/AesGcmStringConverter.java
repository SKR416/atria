package io.github.skr416.atria.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * Transparent AES-256-GCM column encryption. Apply with
 * {@code @Convert(converter = AesGcmStringConverter.class)} on sensitive String fields
 * (webhook secrets, API tokens, personal identifiers). Null stays null.
 * Spring Boot's Hibernate bean container injects the {@link AesGcmCipher}.
 */
@Component
@Converter
public class AesGcmStringConverter implements AttributeConverter<String, String> {

    private final AesGcmCipher cipher;

    public AesGcmStringConverter(AesGcmCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData == null ? null : cipher.decrypt(dbData);
    }
}
