package com.proseller.notiflistener;

import java.util.*;

public class LogStore {
    private static final int MAX = 50;
    private static final List<String> logs = new ArrayList<>();

    public static synchronized void add(String entry) {
        logs.add(entry);
        if (logs.size() > MAX) logs.remove(0);
    }

    public static synchronized List<String> getLogs() {
        return new ArrayList<>(logs);
    }

    public static synchronized void clear() {
        logs.clear();
    }
}
