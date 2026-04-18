package ua.stetsenkoinna.server.service;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SimulationSessionRegistry {

    private final ConcurrentHashMap<String, SimulationSession> sessions = new ConcurrentHashMap<>();

    public SimulationSession create() {
        String id = UUID.randomUUID().toString();
        SimulationSession session = new SimulationSession(id);
        sessions.put(id, session);
        return session;
    }

    public SimulationSession get(String id) {
        return sessions.get(id);
    }

    public void remove(String id) {
        sessions.remove(id);
    }
}
