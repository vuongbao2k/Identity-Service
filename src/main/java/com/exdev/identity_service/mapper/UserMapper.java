package com.exdev.identity_service.mapper;

import com.exdev.identity_service.dto.request.UserCreationRequest;
import com.exdev.identity_service.dto.request.UserUpdateRequest;
import com.exdev.identity_service.dto.response.UserResponse;
import com.exdev.identity_service.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface UserMapper {
    User toUser(UserCreationRequest request);

//    @Mapping(source = "fn", target = "firstName") neu map field khac ten
    UserResponse toUserResponse(User user);

    @Mapping(target = "roles", ignore = true)
    void updateUser(@MappingTarget User user, UserUpdateRequest request);
}
