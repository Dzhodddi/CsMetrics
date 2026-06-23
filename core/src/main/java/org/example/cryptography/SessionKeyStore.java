package org.example.cryptography;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.crypto.SecretKey;

public class SessionKeyStore {
    private final ConcurrentMap<Integer, SecretKey> agentKeys = new ConcurrentHashMap<>();

    public void saveKey(int agentId, SecretKey key) {
        agentKeys.put(agentId, key);
    }

    public SecretKey getKey(int agentId) {
        return agentKeys.get(agentId);
    }
}
