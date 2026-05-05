package com.agent.core.multiagent.a2a;

import com.agent.common.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A2A Protocol client.
 * Handles communication with sub-agents using the A2A protocol.
 *
 * Supports:
 * - Sync/Async invocation based on agent card tags
 * - Streaming/Non-streaming response based on output modes
 * - SSE-based streaming for real-time responses
 */
@Slf4j
public class A2AClient {

    private final OkHttpClient httpClient;
    private final AgentCard agentCard;

    public A2AClient(AgentCard agentCard) {
        this.agentCard = agentCard;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Send a task to the sub-agent (synchronous).
     *
     * @param input     user input text
     * @param metadata  additional metadata
     * @return A2A response message
     */
    @SuppressWarnings("unchecked")
    public A2AMessage sendTask(String input, Map<String, Object> metadata) {
        String taskId = UUID.randomUUID().toString();

        A2AMessage request = A2AMessage.builder()
                .id(UUID.randomUUID().toString())
                .taskId(taskId)
                .type("task")
                .content(A2AMessage.A2AContent.builder()
                        .role("user")
                        .parts(List.of(A2AMessage.A2APart.builder()
                                .type("text")
                                .text(input)
                                .build()))
                        .build())
                .metadata(metadata)
                .build();

        String json = JsonUtils.toJson(request);
        String endpoint = agentCard.getUrl() + "/tasks/send";

        Request httpRequest = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("A2A request failed with status: " + response.code());
            }

            String body = response.body() != null ? response.body().string() : "{}";
            return JsonUtils.fromJson(body, A2AMessage.class);

        } catch (IOException e) {
            throw new RuntimeException("A2A request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Send a task to the sub-agent with streaming response via SSE.
     *
     * @param input         user input text
     * @param metadata      additional metadata
     * @param chunkConsumer callback for each streaming chunk
     * @return future that completes when streaming is done
     */
    public CompletableFuture<Void> sendTaskStreaming(String input, Map<String, Object> metadata,
                                                      Consumer<A2AMessage> chunkConsumer) {
        String taskId = UUID.randomUUID().toString();
        CompletableFuture<Void> future = new CompletableFuture<>();

        A2AMessage request = A2AMessage.builder()
                .id(UUID.randomUUID().toString())
                .taskId(taskId)
                .type("task")
                .content(A2AMessage.A2AContent.builder()
                        .role("user")
                        .parts(List.of(A2AMessage.A2APart.builder()
                                .type("text")
                                .text(input)
                                .build()))
                        .build())
                .metadata(metadata)
                .build();

        String json = JsonUtils.toJson(request);
        String endpoint = agentCard.getUrl() + "/tasks/sendSubscribe";

        Request httpRequest = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .addHeader("Accept", "text/event-stream")
                .build();

        EventSource.Factory factory = EventSources.createFactory(httpClient);
        factory.newEventSource(httpRequest, new EventSourceListener() {
            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                try {
                    A2AMessage chunk = JsonUtils.fromJson(data, A2AMessage.class);
                    chunkConsumer.accept(chunk);
                } catch (Exception e) {
                    log.error("Failed to parse SSE event: {}", data, e);
                }
            }

            @Override
            public void onClosed(EventSource eventSource) {
                future.complete(null);
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, Response response) {
                future.completeExceptionally(t != null ? t : new RuntimeException("SSE connection failed"));
            }
        });

        return future;
    }

    /**
     * Discover agent card from the well-known endpoint.
     *
     * @param baseUrl base URL of the sub-agent
     * @return agent card
     */
    public static AgentCard discoverAgentCard(String baseUrl) {
        OkHttpClient client = new OkHttpClient();
        String url = baseUrl + "/.well-known/agent.json";

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Agent card discovery failed: " + response.code());
            }

            String body = response.body() != null ? response.body().string() : "{}";
            return JsonUtils.fromJson(body, AgentCard.class);

        } catch (IOException e) {
            throw new RuntimeException("Agent card discovery failed: " + e.getMessage(), e);
        }
    }

    public AgentCard getAgentCard() {
        return agentCard;
    }
}
