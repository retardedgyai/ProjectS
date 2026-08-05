package io.github.gyai.projects.beta.activation.track4;

import io.github.gyai.projects.beta.activation.BetaRuntimeModule;

import java.util.LinkedHashMap;
import java.util.Map;

/** Unregistered fallback command surface for the later central integration gate. */
public final class Track4OperatorCommandContributor implements BetaOperatorCommandContributor {
    private final Map<String, BetaRuntimeModule> modules;

    public Track4OperatorCommandContributor(BetaRuntimeModuleProvider provider) {
        LinkedHashMap<String, BetaRuntimeModule> values = new LinkedHashMap<>();
        provider.modules().forEach(module -> values.put(module.id().name().toLowerCase(), module));
        modules = Map.copyOf(values);
    }

    @Override public String prefix() { return "beta staging track4"; }

    @Override public Response execute(Request request) {
        if (request == null || !request.projectsDev()) return new Response(false, java.util.List.of("permission denied"));
        if (request.arguments().isEmpty() || !"status".equals(request.arguments().get(0))) {
            return new Response(false, java.util.List.of("usage: beta staging track4 status"));
        }
        String result = modules.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().state())
                .sorted().collect(java.util.stream.Collectors.joining(" "));
        return new Response(true, java.util.List.of(result));
    }
}
