package com.example.githubmcp.web;

import com.openai.errors.OpenAIException;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Failures talking to OpenAI or the GitHub MCP server are upstream problems, so report 502. */
    @ExceptionHandler({McpError.class, McpTransportException.class, OpenAIException.class})
    ProblemDetail upstreamFailure(RuntimeException ex) {
        log.warn("Upstream call failed", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        problem.setTitle("Upstream service error");
        return problem;
    }
}
