package com.logistics.proyect.group5.dto;

import com.logistics.proyect.group5.model.AuthProvider;
import com.logistics.proyect.group5.model.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryDto {
    private UUID id;
    private String fullName;
    private String email;
    private String phone;
    private UserRole role;
    private AuthProvider provider;
    private boolean enabled;
}
