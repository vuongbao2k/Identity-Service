package com.exdev.identity_service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.exdev.identity_service.dto.request.RoleRequest;
import com.exdev.identity_service.dto.response.RoleResponse;
import com.exdev.identity_service.entity.Role;

@Mapper(componentModel = "spring")
public interface RoleMapper {
    @Mapping(target = "permissions", ignore = true)
    Role toRole(RoleRequest request);

    RoleResponse toRoleResponse(Role role);
}
