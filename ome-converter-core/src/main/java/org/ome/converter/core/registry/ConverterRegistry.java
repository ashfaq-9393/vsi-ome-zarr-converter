package org.ome.converter.core.registry;

import org.ome.converter.core.api.ConverterProvider;
import org.ome.converter.core.exception.UnsupportedFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ConverterRegistry {
    private static final Logger log = LoggerFactory.getLogger(ConverterRegistry.class);
    private static final ConverterRegistry INSTANCE = new ConverterRegistry();

    private final List<ConverterProvider> providers = new CopyOnWriteArrayList<>();

    private ConverterRegistry() {
        reloadProviders();
    }

    public static ConverterRegistry getInstance() {
        return INSTANCE;
    }

    public void reloadProviders() {
        providers.clear();
        ServiceLoader<ConverterProvider> loader = ServiceLoader.load(ConverterProvider.class);
        for (ConverterProvider provider : loader) {
            log.info("Registered converter plugin: {} [{}]", provider.getFormatName(), provider.getFormatDescription());
            providers.add(provider);
        }
    }

    public void registerProvider(ConverterProvider provider) {
        if (provider != null && !providers.contains(provider)) {
            providers.add(provider);
            log.info("Manually registered converter plugin: {}", provider.getFormatName());
        }
    }

    public List<ConverterProvider> getAllProviders() {
        return Collections.unmodifiableList(providers);
    }

    public ConverterProvider findProviderForFile(File file) throws UnsupportedFormatException {
        if (file == null || !file.exists()) {
            throw new UnsupportedFormatException("File does not exist: " + (file == null ? "null" : file.getAbsolutePath()));
        }
        for (ConverterProvider provider : providers) {
            if (provider.supports(file)) {
                return provider;
            }
        }
        throw new UnsupportedFormatException("No registered converter plugin found for file: " + file.getName());
    }

    public ConverterProvider findProviderByFormatName(String formatName) throws UnsupportedFormatException {
        for (ConverterProvider provider : providers) {
            if (provider.getFormatName().equalsIgnoreCase(formatName)) {
                return provider;
            }
        }
        throw new UnsupportedFormatException("No registered converter plugin found for format: " + formatName);
    }
}
