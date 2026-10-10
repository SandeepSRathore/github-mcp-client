package com.example.githubmcp.web;

import com.example.githubmcp.web.dto.ChatRequest;
import com.example.githubmcp.web.dto.ChatResponse;
import com.example.githubmcp.web.dto.ToolInfo;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatClient chatClient;
    private final ToolCallbackProvider mcpTools;

    public ChatController(ChatClient chatClient, ToolCallbackProvider mcpTools) {
        this.chatClient = chatClient;
        this.mcpTools = mcpTools;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String conversationId = request.conversationId() != null && !request.conversationId().isBlank()
                ? request.conversationId()
                : UUID.randomUUID().toString();

        String answer = chatClient.prompt()
                .user(request.message())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        return new ChatResponse(conversationId, answer);
    }

    /** Lists the tools discovered on the GitHub MCP server; useful to verify connectivity and auth. */
    @GetMapping("/tools")
    public List<ToolInfo> tools() {
        return Arrays.stream(mcpTools.getToolCallbacks())
                .map(cb -> new ToolInfo(cb.getToolDefinition().name(), cb.getToolDefinition().description()))
                .toList();
    }
}
