package com.agent.core.event;

import com.agent.common.utils.JsonUtils;
import com.agent.model.event.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Redis-based EventCenter implementation.
 * Uses Redis Pub/Sub for event broadcasting across distributed server nodes.
 *
 * Architecture:
 * 1. Frontend creates a session and establishes WebSocket connection.
 * 2. Machine holding WS connection subscribes to {sessionId}_output topic.
 * 3. Machine holding agent instance subscribes to {sessionId}_input topic.
 * 4. User messages: WS -> EventCenter({sessionId}_input) -> Agent instance.
 * 5. Agent output: Agent -> EventCenter({sessionId}_output) -> All WS connections.
 */
@Slf4j
@Component
public class RedisEventCenter implements EventCenter {

    private static final String INPUT_TOPIC_PREFIX = "agent:event:input:";
    private static final String OUTPUT_TOPIC_PREFIX = "agent:event:output:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;

    /** Track registered listeners for cleanup */
    private final Map<String, MessageListener> inputListeners = new ConcurrentHashMap<>();
    private final Map<String, MessageListener> outputListeners = new ConcurrentHashMap<>();

    public RedisEventCenter(RedisTemplate<String, Object> redisTemplate,
                            RedisMessageListenerContainer listenerContainer) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
    }

    @Override
    public void publishInput(String sessionId, Event event) {
        String topic = INPUT_TOPIC_PREFIX + sessionId;
        String json = JsonUtils.toJson(event);
        log.debug("Publishing input event to topic={}, eventType={}", topic, event.getEventType());
        redisTemplate.convertAndSend(topic, json);
    }

    @Override
    public void publishOutput(String sessionId, Event event) {
        String topic = OUTPUT_TOPIC_PREFIX + sessionId;
        String json = JsonUtils.toJson(event);
        log.debug("Publishing output event to topic={}, eventType={}", topic, event.getEventType());
        redisTemplate.convertAndSend(topic, json);
    }

    @Override
    public void subscribeInput(String sessionId, Consumer<Event> listener) {
        String topic = INPUT_TOPIC_PREFIX + sessionId;
        MessageListener messageListener = (message, pattern) -> {
            try {
                String json = new String(message.getBody());
                // Remove surrounding quotes if Redis serialized as string
                if (json.startsWith("\"") && json.endsWith("\"")) {
                    json = JsonUtils.fromJson(json, String.class);
                }
                Event event = JsonUtils.fromJson(json, Event.class);
                listener.accept(event);
            } catch (Exception e) {
                log.error("Failed to deserialize input event from topic={}", topic, e);
            }
        };

        inputListeners.put(sessionId, messageListener);
        listenerContainer.addMessageListener(messageListener, new ChannelTopic(topic));
        log.info("Subscribed to input topic: {}", topic);
    }

    @Override
    public void subscribeOutput(String sessionId, Consumer<Event> listener) {
        String topic = OUTPUT_TOPIC_PREFIX + sessionId;
        String key = sessionId + ":" + System.identityHashCode(listener);

        MessageListener messageListener = (message, pattern) -> {
            try {
                String json = new String(message.getBody());
                if (json.startsWith("\"") && json.endsWith("\"")) {
                    json = JsonUtils.fromJson(json, String.class);
                }
                Event event = JsonUtils.fromJson(json, Event.class);
                listener.accept(event);
            } catch (Exception e) {
                log.error("Failed to deserialize output event from topic={}", topic, e);
            }
        };

        outputListeners.put(key, messageListener);
        listenerContainer.addMessageListener(messageListener, new ChannelTopic(topic));
        log.info("Subscribed to output topic: {}, subscriberKey={}", topic, key);
    }

    @Override
    public void unsubscribeOutput(String sessionId, String subscriberId) {
        String key = sessionId + ":" + subscriberId;
        MessageListener listener = outputListeners.remove(key);
        if (listener != null) {
            listenerContainer.removeMessageListener(listener);
            log.info("Unsubscribed from output topic for session={}, subscriberId={}", sessionId, subscriberId);
        } else {
            // Fallback: try sessionId-only key for backward compatibility
            listener = outputListeners.remove(sessionId);
            if (listener != null) {
                listenerContainer.removeMessageListener(listener);
                log.info("Unsubscribed from output topic for session={} (legacy key)", sessionId);
            }
        }
    }

    @Override
    public void unsubscribeInput(String sessionId) {
        MessageListener listener = inputListeners.remove(sessionId);
        if (listener != null) {
            listenerContainer.removeMessageListener(listener);
            log.info("Unsubscribed from input topic for session={}", sessionId);
        }
    }
}
