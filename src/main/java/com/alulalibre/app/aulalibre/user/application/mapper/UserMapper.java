package com.alulalibre.app.aulalibre.user.application.mapper;

import com.alulalibre.app.aulalibre.user.application.dto.response.UserResponse;
import com.alulalibre.app.aulalibre.user.domain.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
