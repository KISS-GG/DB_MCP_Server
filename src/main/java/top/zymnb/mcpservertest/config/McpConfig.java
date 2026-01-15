package top.zymnb.mcpservertest.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.zymnb.mcpservertest.service.McpService;

/**
 * MCP 服务器配置类
 * 负责配置 MCP 工具的回调提供者，将 McpService 中的方法注册为 MCP 工具
 */
@Configuration
public class McpConfig {

    /**
     * 配置 MCP 工具回调提供者
     * 使用 MethodToolCallbackProvider 自动扫描 McpService 中标注了 @Tool 注解的方法，
     * 并将这些方法注册为可被 AI 调用的 MCP 工具
     *
     * @param service MCP 服务实例，包含所有工具方法
     * @return 工具回调提供者，用于 MCP 服务器调用工具
     */
    @Bean
    public ToolCallbackProvider tools(McpService service) {
        return MethodToolCallbackProvider.builder().toolObjects(service).build();
    }
}
