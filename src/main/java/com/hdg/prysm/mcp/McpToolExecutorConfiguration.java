package com.hdg.prysm.mcp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class McpToolExecutorConfiguration {

    @Bean(destroyMethod = "shutdown")
    ExecutorService mcpToolExecutor(@Value("${prysm.agentic.tool-threads:8}") int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("MCP tool thread count must be positive");
        }
        return Executors.newFixedThreadPool(threads, Thread.ofPlatform().name("prysm-mcp-tool-", 0).factory());
    }
}
