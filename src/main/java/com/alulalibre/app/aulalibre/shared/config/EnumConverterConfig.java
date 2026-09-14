package com.alulalibre.app.aulalibre.shared.config;

import com.alulalibre.app.aulalibre.room.domain.enums.RoomType;
import com.alulalibre.app.aulalibre.roomrequest.domain.enums.RoomRequestReason;
import com.alulalibre.app.aulalibre.roomrequest.domain.enums.RoomRequestStatus;
import com.alulalibre.app.aulalibre.user.domain.enums.UserRole;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Query params (e.g. {@code ?type=Aula}, {@code ?status=pendiente}) go
 * through Spring's ConversionService, not Jackson — so the Spanish
 * {@code @JsonCreator} on each enum isn't picked up automatically. These
 * converters reuse the exact same {@code fromLabel(...)} so there is one
 * single source of truth for the label mapping.
 */
@Configuration
public class EnumConverterConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(@NonNull FormatterRegistry registry) {
        // The 3-arg overload passes explicit Class tokens: a lambda cast to
        // Converter<String, X> loses its generic types to erasure, and
        // GenericConversionService needs them to register the mapping.
        registry.addConverter(String.class, RoomType.class, RoomType::fromLabel);
        registry.addConverter(String.class, RoomRequestStatus.class, RoomRequestStatus::fromLabel);
        registry.addConverter(String.class, RoomRequestReason.class, RoomRequestReason::fromLabel);
        registry.addConverter(String.class, UserRole.class, UserRole::fromLabel);
    }
}
