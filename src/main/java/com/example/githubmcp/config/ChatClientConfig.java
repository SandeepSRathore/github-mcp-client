package com.example.githubmcp.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    private static final String SYSTEM_PROMPT = """
            You are a GitHub assistant. Use the available GitHub tools to answer questions about
            repositories, issues, pull requests and code. Never invent data: if a tool returns
            nothing, say so. Always cite issue/PR numbers and their URLs. If the owner/repo is
            ambiguous or missing, ask the user to clarify instead of guessing.

            Issues and pull requests share one number sequence, so neither the number nor the
            word the user chose ("issue" or "PR") tells you which it is. To look up a number:
            1. Call issue_read first; it works for both issues and pull requests.
            2. If the result has a pull_request field or an html_url containing /pull/, it is a
               pull request: also call pull_request_read to get its merged status.
            Only say a number does not exist after issue_read finds nothing.

            Name it correctly in your answer, starting with "Pull request #N" or "Issue #N". Only
            if the user's message explicitly said "issue" or "PR" and that was wrong, briefly
            point that out. State its status exactly: a pull request is open, merged (with merge
            date), or closed without merging; an issue is open or closed.
            """;

    /**
     * MCP tools are not registered with ChatClient automatically, so they are wired in here as defaults.
     */
    @Bean
    ChatClient chatClient(ChatClient.Builder builder, SyncMcpToolCallbackProvider mcpTools, ChatMemory chatMemory) {
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(mcpTools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
