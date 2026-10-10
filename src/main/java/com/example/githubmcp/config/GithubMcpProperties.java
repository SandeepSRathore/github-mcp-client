package com.example.githubmcp.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for the remote GitHub MCP server.
 *
 * @param connectionName name of the streamable-http connection these settings apply to
 * @param token          GitHub personal access token, sent as a Bearer token
 * @param toolsets       comma-separated GitHub MCP toolsets to expose (keeps the tool list small)
 * @param readOnly       when true, the server only exposes read-only tools
 */
@Validated
@ConfigurationProperties("github.mcp")
public record GithubMcpProperties(
        String connectionName,
        @NotBlank(message = "GitHub PAT is missing: set the GITHUB_PAT environment variable") String token,
        String toolsets,
        boolean readOnly) {

    public GithubMcpProperties {
        if (connectionName == null || connectionName.isBlank()) {
            connectionName = "github";
        }
    }
}
