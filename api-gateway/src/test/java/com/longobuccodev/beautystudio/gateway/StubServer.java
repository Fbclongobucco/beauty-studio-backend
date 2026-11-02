package com.longobuccodev.beautystudio.gateway;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/** Servidor HTTP minimo no papel do booking-service e do MinIO. */
final class StubServer {

    static HttpServer start(long slowMillis) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.createContext("/api/services", ex -> {
                byte[] body = "{\"categories\":[]}".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.getResponseHeaders().add("X-Path", ex.getRequestURI().getPath());
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
                ex.close();
            });
            server.createContext("/api/set-cookie", ex -> {
                ex.getResponseHeaders().add("Set-Cookie", "admin_session=sessao-da-maria; Path=/; HttpOnly");
                ex.sendResponseHeaders(204, -1);
                ex.close();
            });
            server.createContext("/api/echo-cookie", ex -> {
                String cookie = String.valueOf(ex.getRequestHeaders().getFirst("Cookie"));
                byte[] body = cookie.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
                ex.close();
            });
            server.createContext("/api/echo-forwarded", ex -> {
                String xff = String.valueOf(ex.getRequestHeaders().getFirst("X-Forwarded-For"));
                byte[] body = xff.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
                ex.close();
            });
            server.createContext("/api/slow", ex -> {
                try {
                    Thread.sleep(slowMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                ex.sendResponseHeaders(204, -1);
                ex.close();
            });
            server.createContext("/service-photos/", ex -> {
                byte[] body = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
                ex.getResponseHeaders().add("Content-Type", "image/jpeg");
                ex.getResponseHeaders().add("X-Path", ex.getRequestURI().getPath());
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
                ex.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private StubServer() {
    }
}
