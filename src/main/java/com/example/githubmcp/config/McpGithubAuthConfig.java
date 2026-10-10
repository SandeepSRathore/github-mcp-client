package com.example.githubmcp.config;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds GitHub authentication and toolset headers to every request sent to the GitHub MCP server.
 */
@Configuration
public class McpGithubAuthConfig {

    @Bean
    McpClientCustomizer<HttpClientStreamableHttpTransport.Builder> githubMcpTransportCustomizer(
            GithubMcpProperties props) {
        return (connectionName, builder) -> {
            if (!props.connectionName().equals(connectionName)) {
                return;
            }
            builder.httpRequestCustomizer((request, method, uri, body, context) -> {
                request.header("Authorization", "Bearer " + props.token());
                if (props.toolsets() != null && !props.toolsets().isBlank()) {
                    request.header("X-MCP-Toolsets", props.toolsets());
                }
                if (props.readOnly()) {
                    request.header("X-MCP-Readonly", "true");
                }
            });
        };
    }
}
