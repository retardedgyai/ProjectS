package io.github.gyai.projects.gathering;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class GatheringNodeRegistry implements AutoCloseable {
    private final int maximumNodes;
    private final Map<String, GatheringNode> nodes = new LinkedHashMap<>();
    private boolean closed;

    public GatheringNodeRegistry(int maximumNodes) {
        if (maximumNodes <= 0) throw new IllegalArgumentException("Invalid node bound");
        this.maximumNodes = maximumNodes;
    }

    public synchronized void register(GatheringNode node) {
        Objects.requireNonNull(node, "node");
        if (closed) throw new IllegalStateException("Node registry is closed");
        GatheringNode.Snapshot snapshot = node.snapshot();
        if (nodes.containsKey(snapshot.nodeId())) {
            throw new IllegalArgumentException("Duplicate node ID: " + snapshot.nodeId());
        }
        if (nodes.size() >= maximumNodes) {
            throw new IllegalStateException("Node registry limit reached");
        }
        nodes.put(snapshot.nodeId(), node);
    }

    public synchronized Map<String, GatheringNode.Snapshot> snapshot() {
        LinkedHashMap<String, GatheringNode.Snapshot> result = new LinkedHashMap<>();
        nodes.forEach((id, node) -> result.put(id, node.snapshot()));
        return Collections.unmodifiableMap(result);
    }

    public synchronized int size() {
        return nodes.size();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        nodes.values().forEach(GatheringNode::close);
        nodes.clear();
    }
}
