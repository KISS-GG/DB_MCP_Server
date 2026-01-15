package top.zymnb.mcpservertest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DB-MCP-Server 应用启动类
 * <p>
 * 这是一个基于 Spring Boot 3.5.9 和 Spring AI 1.1.2 的 MCP (Model Context Protocol) 服务器项目，
 * 用于提供数据库连接和 SQL 执行能力。
 * </p>
 * <p>
 * 主要功能：
 * <ul>
 *   <li>支持多种数据库：MySQL、PostgreSQL、Oracle、SQL Server、KingBase</li>
 *   <li>提供6个MCP工具：executeQuery、executeWrite、confirmWrite、executeBatch、getMetadata、executeDDL</li>
 *   <li>使用 WebFlux 实现响应式异步通信</li>
 *   <li>提供 SSE (Server-Sent Events) 端点进行实时通信</li>
 * </ul>
 * </p>
 *
 * @author zym
 * @since 1.0.0
 */
@SpringBootApplication
public class McpServerTestApplication {

    /**
     * 应用程序入口方法
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(McpServerTestApplication.class, args);
    }

}
