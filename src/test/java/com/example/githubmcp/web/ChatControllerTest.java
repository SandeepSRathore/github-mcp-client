package com.example.githubmcp.web;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.modelcontextprotocol.spec.McpTransportException;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    ChatClient chatClient;

    @MockitoBean
    ToolCallbackProvider mcpTools;

    @Test
    void chatReturnsModelAnswerAndGeneratesConversationId() throws Exception {
        when(chatClient.prompt().user(any(String.class)).advisors(anyAdvisorSpec())
                .call().content()).thenReturn("Issue #1 is about X");

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Summarize issue #1 in owner/repo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Issue #1 is about X"))
                .andExpect(jsonPath("$.conversationId").isNotEmpty());
    }

    @Test
    void chatKeepsProvidedConversationId() throws Exception {
        when(chatClient.prompt().user(any(String.class)).advisors(anyAdvisorSpec())
                .call().content()).thenReturn("ok");

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\",\"conversationId\":\"abc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("abc"));
    }

    @Test
    void chatRejectsBlankMessage() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mcpFailureMapsToBadGateway() throws Exception {
        when(chatClient.prompt().user(any(String.class)).advisors(anyAdvisorSpec())
                .call().content()).thenThrow(new McpTransportException("401 Unauthorized"));

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("401 Unauthorized"));
    }

    @Test
    void toolsListsDiscoveredMcpTools() throws Exception {
        ToolCallback tool = mock(ToolCallback.class);
        when(tool.getToolDefinition()).thenReturn(
                ToolDefinition.builder().name("issue_read").description("Get an issue").inputSchema("{}").build());
        when(mcpTools.getToolCallbacks()).thenReturn(new ToolCallback[] {tool});

        mvc.perform(get("/api/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("issue_read"));
    }

    private static Consumer<ChatClient.AdvisorSpec> anyAdvisorSpec() {
        return any();
    }
}
