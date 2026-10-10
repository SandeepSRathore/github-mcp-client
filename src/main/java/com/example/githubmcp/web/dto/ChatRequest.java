package com.example.githubmcp.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(@NotBlank String message, String conversationId) {
}
