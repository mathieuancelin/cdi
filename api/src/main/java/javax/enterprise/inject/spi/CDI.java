package javax.enterprise.inject.spi;

import javax.enterprise.inject.Instance;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides access to the current container.
 * 
 * @author Pete Muir
 * @author Mathieu Ancelin
 * 
 */
public abstract class CDI<T> implements Instance<T> {

    /**
     * Default instance of {@link CDIProviderResolver} using ServiceLoader like mechanism.
     */
    protected static final CDIProviderResolver DEFAULT_RESOLVER = new DefaultProviderResolver();
    
    /**
     * Default instance of {@link CDIBootstrap} using {@link DEFAULT_RESOLVER}.
     */
    private static final CDIBoostrap DEFAULT_BOOTSTRAP = new CDIBoostrap(DEFAULT_RESOLVER);
    
    /**
     * Cache for CDIBootsrap instances from resolvers.
     */
    private static final ConcurrentHashMap<CDIProviderResolver, CDIBoostrap> RESOLVERS =
            new ConcurrentHashMap<CDIProviderResolver, CDIBoostrap>();

    /**
     * <p>
     * Get the CDI instance that provides access to the current container.
     * </p>
     * 
     * <p>
     * If there are no providers available, an {@link IllegalStateException} is thrown, otherwise the
     * first provider which can access the container is used.
     * </p>
     * 
     * @throws IllegalStateException if no CDI provider is available
     * 
     */
    public static <T> CDI<T> current() {
        return DEFAULT_BOOTSTRAP.current();
    }
    
    /**
     * <p>
     * Get the CDI instance that provides access to the current container.
     * </p>
     * <p>
     * Search CDI instance using {@link CDIProviderResolver}.
     * </p>
     * 
     * <p>
     * If there are no providers available, an {@link IllegalStateException} is thrown, otherwise the
     * first provider which can access the container is used.
     * </p>
     * 
     * @throws IllegalStateException if no CDI provider is available
     * 
     */
    public static <T> CDI<T> currentFromResolver(CDIProviderResolver resolver) {
        if (!RESOLVERS.contains(resolver)) {
            RESOLVERS.putIfAbsent(resolver, new CDIBoostrap(resolver));
        }
        return RESOLVERS.get(resolver).current();
    }

    /**
     * Default {@link CDIProviderResolver} implementation using ServiceLoader mechanism.
     */
    private static class DefaultProviderResolver implements CDIProviderResolver {
        
        /**
         * <p>
         * Get the set of available CDIProvider from ServiceLoader provider files.
         * </p>
         * 
         * <p>
         * If there are no providers available, an {@link IllegalStateException} is thrown.
         * </p>
         * 
         * @throws IllegalStateException if no CDI provider is available
         */
        public Set<CDIProvider> getProviders() {
            Set<CDIProvider> providers = new LinkedHashSet<CDIProvider>();
            try {
                final ClassLoader loader = CDI.class.getClassLoader();
                final Enumeration<URL> resources;
                if (loader != null) {
                    resources = loader.getResources("META-INF/services/" + CDIProvider.class.getName());
                } else {
                    //this should not happen unless the CDI api is on the boot class path
                    resources = ClassLoader.getSystemResources("META-INF/services/" + CDIProvider.class.getName());
                }

                final Set<String> names = new HashSet<String>();
                while (resources.hasMoreElements()) {
                    URL url = resources.nextElement();
                    InputStream is = url.openStream();
                    try {
                        names.addAll(providerNamesFromReader(new BufferedReader(new InputStreamReader(is))));
                    } finally {
                        is.close();
                    }
                }
                for (String s : names) {
                    final Class<CDIProvider> providerClass = (Class<CDIProvider>) Class.forName(s, true, loader);
                    providers.add(providerClass.newInstance());
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            } catch (InstantiationException e) {
                throw new IllegalStateException(e);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
            return providers;
        }

        // Helper methods

        private static final Pattern nonCommentPattern = Pattern.compile("^([^#]+)");

        private static Set<String> providerNamesFromReader(BufferedReader reader) throws IOException {
            Set<String> names = new HashSet<String>();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                Matcher m = nonCommentPattern.matcher(line);
                if (m.find()) {
                    names.add(m.group().trim());
                }
            }
            return names;
        }
    }

    /**
     * Bootstrap class to get the current CDI instance using a specific resolver
     * that provides access to the current container from the current 
     * CDIProvider resolver.
     */
    private static class CDIBoostrap {

        protected volatile Set<CDIProvider> providers = null;

        private final CDIProviderResolver resolver;

        public CDIBoostrap(CDIProviderResolver resolver) {
            this.resolver = resolver;
        }

        /**
         * <p>
         * Get the CDI instance that provides access to the current container.
         * </p>
         * 
         * <p>
         * If there are no providers available, an {@link IllegalStateException} is thrown, otherwise the
         * first provider which can access the container is used.
         * </p>
         * 
         * @throws IllegalStateException if no CDI provider is available
         * 
         */
        public <T> CDI<T> current() {
            CDI<T> cdi = null;
            if (providers == null) {
                synchronized (this) {
                    if (providers == null) {
                        providers = resolver.getProviders();
                    }
                }
            }
            for (CDIProvider provider : providers) {
                cdi = provider.getCDI();
                if (cdi != null) {
                    break;
                }
            }
            if (cdi == null) {
                throw new IllegalStateException("Unable to access CDI");
            }
            return cdi;
        }
    }

    /**
     * Get the CDI BeanManager for the current context
     * 
     * @return
     */
    public abstract BeanManager getBeanManager();
}
