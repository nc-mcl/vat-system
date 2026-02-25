package com.netcompany.vat.api.config;

import com.netcompany.vat.api.exception.EntityNotFoundException;
import com.netcompany.vat.domain.JurisdictionCode;
import com.netcompany.vat.domain.dk.DkJurisdictionPlugin;
import com.netcompany.vat.domain.jurisdiction.JurisdictionPlugin;
import com.netcompany.vat.taxengine.TaxEngine;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Registry of active {@link JurisdictionPlugin} instances.
 *
 * <p>Each jurisdiction has exactly one plugin registered at startup. Adding a new
 * jurisdiction requires only a new plugin implementation and a registration entry here.
 *
 * <p>Plugins are stateless — a single shared instance is safe for concurrent use.
 */
@Component
public class JurisdictionRegistry {

    private final Map<JurisdictionCode, JurisdictionPlugin> plugins;

    public JurisdictionRegistry() {
        Map<JurisdictionCode, JurisdictionPlugin> map = new EnumMap<>(JurisdictionCode.class);
        map.put(JurisdictionCode.DK, new DkJurisdictionPlugin());
        this.plugins = Collections.unmodifiableMap(map);
    }

    /**
     * Returns the plugin for the given jurisdiction code.
     *
     * @throws EntityNotFoundException if no plugin is registered for the code
     */
    public JurisdictionPlugin getPlugin(JurisdictionCode code) {
        JurisdictionPlugin plugin = plugins.get(code);
        if (plugin == null) {
            throw new EntityNotFoundException("No jurisdiction plugin registered for: " + code);
        }
        return plugin;
    }

    /**
     * Returns a {@link TaxEngine} bound to the plugin for the given jurisdiction.
     *
     * <p>{@link TaxEngine} is stateless — a new instance is created per call, which is safe
     * and avoids shared-state concerns.
     */
    public TaxEngine getTaxEngine(JurisdictionCode code) {
        return new TaxEngine(getPlugin(code));
    }
}
