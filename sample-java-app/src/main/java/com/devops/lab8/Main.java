package com.devops.lab8;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class Main {
    public static void main(String[] args) throws Exception {
        Calculator calc = new Calculator();
        long startTime = System.currentTimeMillis();
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "3000"));
        String host = InetAddress.getLocalHost().getHostName();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> {
            String json = String.format(
                "{\"message\":\"DevOps Lab 7+8 — Unified CI/CD Pipeline\",\"version\":\"2.0.0\",\"calcDemo\":\"10+5=%d\",\"timestamp\":\"%s\",\"host\":\"%s\"}",
                calc.add(10, 5), Instant.now().toString(), host
            );
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.createContext("/health", exchange -> {
            double uptime = (System.currentTimeMillis() - startTime) / 1000.0;
            String json = String.format("{\"status\":\"OK\",\"uptime\":%.3f}", uptime);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        System.out.println("Unified CI/CD App running on :" + port);
    }
}
