package com.exdev.identity_service.mapper;

import com.exdev.identity_service.dto.request.PermissionRequest;
import com.exdev.identity_service.dto.request.UserCreationRequest;
import com.exdev.identity_service.dto.request.UserUpdateRequest;
import com.exdev.identity_service.dto.response.PermissionResponse;
import com.exdev.identity_service.dto.response.UserResponse;
import com.exdev.identity_service.entity.Permission;
import com.exdev.identity_service.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PermissionMapper {
    Permission toPermission(PermissionRequest request);
    PermissionResponse toPermissionResponse(Permission permission);
}
