package com.jobtracker.dto;

public record AuthResponse(
        String token,
        String email,
        String fullName
) {}
