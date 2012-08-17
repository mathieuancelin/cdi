package javax.enterprise.inject.spi;

import java.util.Set;

/**
 * Interface implemented by a CDI provider resolver to provide access to the current CDI Provider
 * in modular environment.
 * 
 * @author Mathieu Ancelin
 */
public interface CDIProviderResolver {
    
    /**
     * Provides access to available CDI providers
     * 
     * @return the set of available CDI providers
     */
    Set<CDIProvider> getProviders(); 
}
