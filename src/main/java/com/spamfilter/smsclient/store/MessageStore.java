package com.spamfilter.smsclient.store;

import com.spamfilter.smsclient.model.Direction;
import com.spamfilter.smsclient.model.SmsMessage;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory message history. Not persisted across restarts -
 * swap for a real datastore later if you need durability.
 */
public class MessageStore {

    private final Map<String, SmsMessage> messages = new ConcurrentHashMap<>();

    public void add(SmsMessage message) {
        messages.put(message.getId(), message);
    }

    public SmsMessage get(String id) {
        return messages.get(id);
    }

    public List<SmsMessage> list(Direction direction, int limit) {
        return messages.values().stream()
                .filter(m -> direction == null || m.getDirection() == direction)
                .sorted(Comparator.comparing(SmsMessage::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
}
