package it.overzoom.ordinainchat.data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProductNameCache {
    private final Map<Long, String> map = new ConcurrentHashMap<>();

    public String get(long id) {
        return map.get(id);
    }

    public void put(long id, String name) {
        if (id > 0 && name != null && !name.isBlank())
            map.put(id, name);
    }

    public void putAll(Map<Long, String> m) {
        if (m != null)
            map.putAll(m);
    }
}
