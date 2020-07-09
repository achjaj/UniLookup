package com.velitar.unilookup;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Cache {
    private final int PERIOD = 1;
    private final TimeUnit PERIOD_UNIT = TimeUnit.MINUTES;

    private HashMap<String, CacheValue> memory = new HashMap<>();
    private CacheSettings settings;
    private String root;
    private UniLookup lookup;

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private Runnable cleaner = new Runnable() {
        @Override
        public void run() {
            long current = System.currentTimeMillis();
            memory.forEach((key, value) -> {
                long diff = current - value.lastAccess;
                if (TimeUnit.MILLISECONDS.toSeconds(diff) > settings.cacheTime)
                    memory.remove(key);
            });
        }
    };


    Cache(String root, CacheSettings settings, UniLookup lookup) throws IOException {
        this.settings = settings;
        this.root = root;
        this.lookup = lookup;

        if (settings.use == CacheSettings.Type.CACHE) {
            startCleaner();
        }

        if (settings.use == CacheSettings.Type.RAM)
            fillMemory();
    }

    public void setSettings(CacheSettings settings) throws IOException {
        this.settings = settings;
        memory = new HashMap<>();

        if (settings.use != CacheSettings.Type.CACHE)
            scheduler.shutdown();

        if (settings.use == CacheSettings.Type.CACHE && (scheduler.isShutdown() || scheduler.isTerminated()))
            startCleaner();

        if (settings.use == CacheSettings.Type.RAM)
            fillMemory();
    }

    private void fillMemory() throws IOException {
        for (String group : lookup.getGroupsAcronyms()) {
            memory.put(group, new CacheValue(-1, getFromDrive(group)));
        }
    }

    private void startCleaner() {
        scheduler.scheduleAtFixedRate(cleaner, PERIOD, PERIOD, PERIOD_UNIT);
    }

    private void removeIfFull() {
        if (memory.size() > settings.cacheMaxEntries) {
            String maxTimeKey= null;
            long maxTime = 0;

            for (String key : memory.keySet()) {
                long diff = System.currentTimeMillis() - memory.get(key).lastAccess;
                if (diff > maxTime) {
                    maxTime = diff;
                    maxTimeKey = key;
                }
            }

            if (maxTimeKey != null)
                memory.remove(maxTimeKey);
        }
    }

    private JSONObject getFromCache(String name) throws IOException {
        CacheValue value;
        if (!memory.containsKey(name)) {
            value = new CacheValue(System.currentTimeMillis(), getFromDrive(name));
            removeIfFull();
            memory.put(name, value);
        } else {
            value = memory.get(name);
            value.lastAccess = System.currentTimeMillis();
        }

        return value.value;
    }

    private JSONObject getFromDrive(String name) throws IOException {
        String source = String.join("", Files.readAllLines(Paths.get(root, name)));
        return new JSONObject(source);
    }

    JSONObject getGroup(String name) throws IOException {
        switch (settings.use) {
            case CACHE:
                return getFromCache(name);
            case RAM:
                return memory.get(name).value;
            default:
                return getFromDrive(name);
        }
    }

    private static class CacheValue {
        private long lastAccess;
        private JSONObject value;

        CacheValue(long lastAccess, JSONObject value) {
            this.lastAccess = lastAccess;
            this.value = value;
        }
    }

    public static class CacheSettings {
        private Type use = Type.CACHE;
        private long cacheMaxEntries = 14;
        private long cacheTime = 60; //seconds
        public CacheSettings() {}

        public CacheSettings(Type use, long cacheMaxEntries, long cacheTime) {
            this.use = use;
            this.cacheMaxEntries = cacheMaxEntries;
            this.cacheTime = cacheTime;
        }

        public Type using() {
            return use;
        }

        public void use(Type type) {
            this.use = type;
        }

        public long getCacheMaxEntries() {
            return cacheMaxEntries;
        }

        public void setCacheMaxEntries(long cacheMaxEntries) {
            this.cacheMaxEntries = cacheMaxEntries;
        }

        public long getCacheTime() {
            return cacheTime;
        }

        public void setCacheTime(long cacheTime) {
            this.cacheTime = cacheTime;
        }

        public enum Type {
            CACHE, DRIVE, RAM
        }
    }
}
