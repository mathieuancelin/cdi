package org.jboss.cdi.api.test;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.CDIProvider;
import javax.enterprise.inject.spi.CDIProviderResolver;
import javax.enterprise.util.TypeLiteral;
import junit.framework.Assert;
import org.testng.annotations.Test;

public class CDIProviderResolverTest {

    private static CDIProviderResolver emptyCDIProviderResolver = new CDIProviderResolver() {

        public Set<CDIProvider> getProviders() {
            return Collections.emptySet();
        }
    };
    
    private static CDI<?> cdiInstance = new CDI<Object>() {

        @Override
        public BeanManager getBeanManager() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public Instance<Object> select(Annotation... qualifiers) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public <U> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public <U> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public boolean isUnsatisfied() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public boolean isAmbiguous() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public Iterator<Object> iterator() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public Object get() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    };
    
    private static CDIProvider testProvider = new CDIProvider() {

        public <T> CDI<T> getCDI() {
            return (CDI<T>) cdiInstance;
        }
    };

    @Test(expectedExceptions = IllegalStateException.class)
    public void dummyDefaultTest() {
        CDI<Object> instance = CDI.current();
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void dummyResolverTest() {
        CDI<Object> instance = CDI.currentFromResolver(emptyCDIProviderResolver);
    }

    @Test
    public void resolverTest() {
        CDI<Object> instance = CDI.currentFromResolver(new CDIProviderResolver() {

            public Set<CDIProvider> getProviders() {
                return Collections.<CDIProvider>singleton(testProvider);
            }
        });
        Assert.assertNotNull(instance);
        Assert.assertSame(instance, cdiInstance);
    }

    @Test
    public void threadedResolverTest() throws Exception {
        Semaphore s1 = new Semaphore(0);
        Semaphore s2 = new Semaphore(0);
        CustomResolver resolver = new CustomResolver(testProvider);
        CDIThread thread1 = new CDIThread(s1, s2, resolver);
        CDIThread thread2 = new CDIThread(s2, s1, resolver);
        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();
        Assert.assertNotNull(thread1.instance);
        Assert.assertNotNull(thread2.instance);
        Assert.assertSame(thread1.instance, cdiInstance);
        Assert.assertSame(thread2.instance, cdiInstance);
        Assert.assertSame(thread1.instance, thread2.instance);
    }
    
    private static class CustomResolver implements CDIProviderResolver {
        
        private final CDIProvider provider; 
        
        private final CountDownLatch latch = new CountDownLatch(1);

        public CustomResolver(CDIProvider provider) {
            this.provider = provider;
        }

        public Set<CDIProvider> getProviders() {
            if (latch.getCount() != 0) {
                latch.countDown();
                System.out.println("[TRACE] first call: next should be cached");
                return Collections.<CDIProvider>singleton( provider );
            } else {
                System.err.println("[TRACE] second call: not cached");
                return Collections.emptySet();
            }
        }
    }

    private static class CDIThread extends Thread {

        private final Semaphore s1;
        private final Semaphore s2;
        private CDI<Object> instance;
        private final CDIProviderResolver resolver;
        
        public CDIThread(Semaphore s1, Semaphore s2, CDIProviderResolver resolver) {
            this.s1 = s1;
            this.s2 = s2;
            this.resolver = resolver;
        }

        @Override
        public void run() {
            try {
                s1.release();
                s2.acquire();
                this.instance = CDI.currentFromResolver( resolver );
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
