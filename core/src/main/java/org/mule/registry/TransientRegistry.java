/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.registry;

import org.mule.api.MuleContext;
import org.mule.api.MuleException;
import org.mule.api.agent.Agent;
import org.mule.api.endpoint.ImmutableEndpoint;
import org.mule.api.lifecycle.Disposable;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.model.Model;
import org.mule.api.registry.InjectProcessor;
import org.mule.api.registry.MuleRegistry;
import org.mule.api.registry.ObjectProcessor;
import org.mule.api.registry.PreInitProcessor;
import org.mule.api.registry.RegistrationException;
import org.mule.api.service.Service;
import org.mule.api.transformer.Transformer;
import org.mule.api.transport.Connector;
import org.mule.config.i18n.MessageFactory;
import org.mule.lifecycle.phases.NotInLifecyclePhase;
import org.mule.util.CollectionUtils;
import org.mule.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.collections.Predicate;
import org.apache.commons.collections.functors.InstanceofPredicate;
import org.apache.commons.logging.Log;

/**
 * Use the registryLock when reading/writing/iterating over the contents of the registry hashmap.
 */
//@ThreadSafe
public class TransientRegistry extends AbstractRegistry
{
    public static final String REGISTRY_ID = "org.mule.Registry.Transient";

    private final RegistryMap registryMap = new RegistryMap(logger);

    public TransientRegistry(MuleContext muleContext)
    {
        this(REGISTRY_ID, muleContext);
    }

    public TransientRegistry(String id, MuleContext muleContext)
    {
        super(id, muleContext);
        putDefaultEntriesIntoRegistry();
    }

    private void putDefaultEntriesIntoRegistry()
    {
        Map<String, Object> processors = new HashMap<String, Object>();
        processors.put("_muleContextProcessor", new MuleContextProcessor(muleContext));
        //processors("_muleNotificationProcessor", new NotificationListenersProcessor(muleContext));
        processors.put("_muleExpressionEvaluatorProcessor", new ExpressionEvaluatorProcessor(muleContext));
        processors.put("_muleExpressionEnricherProcessor", new ExpressionEnricherProcessor(muleContext));
        processors.put("_muleLifecycleStateInjectorProcessor", new LifecycleStateInjectorProcessor(getLifecycleManager().getState()));
        processors.put("_muleLifecycleManager", getLifecycleManager());
        registryMap.putAll(processors);
    }

    @Override
    protected void doInitialise() throws InitialisationException
    {
        applyProcessors(lookupObjects(Connector.class), null);
        applyProcessors(lookupObjects(Transformer.class), null);
        applyProcessors(lookupObjects(ImmutableEndpoint.class), null);
        applyProcessors(lookupObjects(Agent.class), null);
        applyProcessors(lookupObjects(Model.class), null);
        applyProcessors(lookupObjects(Service.class), null);
        applyProcessors(lookupObjects(Object.class), null);
    }

    @Override
    protected void doDispose()
    {
        registryMap.clear();
    }

    protected Map<String, Object> applyProcessors(Map<String, Object> objects)
    {
        if (objects == null)
        {
            return null;
        }

        Map<String, Object> results = new HashMap<String, Object>();
        for (Map.Entry<String, Object> entry : objects.entrySet())
        {
            // We do this inside the loop in case the map contains ObjectProcessors
            Collection<ObjectProcessor> processors = lookupObjects(ObjectProcessor.class);
            for (ObjectProcessor processor : processors)
            {
                Object result = processor.process(entry.getValue());
                if (result != null)
                {
                    results.put(entry.getKey(), result);
                }
            }
        }
        return results;
    }


    public void registerObjects(Map<String, Object> objects) throws RegistrationException
    {
        if (objects == null)
        {
            return;
        }

        for (Map.Entry<String, Object> entry : objects.entrySet())
        {
            registerObject(entry.getKey(), entry.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    public <T> Map<String, T> lookupByType(Class<T> type)
    {
        final Map<String, T> results = new HashMap<String, T>();
        try
        {
            registryMap.lockForReading();

            for (Map.Entry<String, Object> entry : registryMap.entrySet())
            {
                final Class<?> clazz = entry.getValue().getClass();
                if (type.isAssignableFrom(clazz))
                {
                    results.put(entry.getKey(), (T) entry.getValue());
                }
            }
        }
        finally
        {
            registryMap.unlockForReading();
        }

        return results;
    }

    public <T> T lookupObject(String key)
    {
        return registryMap.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> Collection<T> lookupObjects(Class<T> returntype)
    {
        return (Collection<T>) registryMap.select(new InstanceofPredicate(returntype));
    }

    /**
     * Will fire any lifecycle methods according to the current lifecycle without actually
     * registering the object in the registry.  This is useful for prototype objects that are created per request and would
     * clutter the registry with single use objects.
     *
     * @param object the object to process
     * @return the same object with lifecycle methods called (if it has any)
     * @throws org.mule.api.MuleException if the registry fails to perform the lifecycle change for the object.
     */
    Object applyLifecycle(Object object) throws MuleException
    {
        getLifecycleManager().applyCompletedPhases(object);
        return object;
    }

    Object applyLifecycle(Object object, String phase) throws MuleException
    {
        getLifecycleManager().applyPhase(object, NotInLifecyclePhase.PHASE_NAME, phase);
        return object;
    }

    Object applyProcessors(Object object, Object metadata)
    {
        Object theObject = object;

        if(!hasFlag(metadata, MuleRegistry.INJECT_PROCESSORS_BYPASS_FLAG))
        {
            //Process injectors first
            Collection<InjectProcessor> injectProcessors = lookupObjects(InjectProcessor.class);
            for (InjectProcessor processor : injectProcessors)
            {
                theObject = processor.process(theObject);
            }
        }

        if(!hasFlag(metadata, MuleRegistry.PRE_INIT_PROCESSORS_BYPASS_FLAG))
        {
            //Then any other processors
            Collection<PreInitProcessor> processors = lookupObjects(PreInitProcessor.class);
            for (PreInitProcessor processor : processors)
            {
                theObject = processor.process(theObject);
                if(theObject==null)
                {
                    return null;
                }
            }
        }
        return theObject;
    }

    /**
     * Allows for arbitary registration of transient objects
     *
     * @param key
     * @param value
     */
    public void registerObject(String key, Object value) throws RegistrationException
    {
        registerObject(key, value, Object.class);
    }

    /**
     * Allows for arbitrary registration of transient objects
     */
    public void registerObject(String key, Object object, Object metadata) throws RegistrationException
    {
        checkDisposed();
        if (StringUtils.isBlank(key))
        {
            throw new RegistrationException(MessageFactory.createStaticMessage("Attempt to register object with no key"));
        }

        if (logger.isDebugEnabled())
        {
            logger.debug(String.format("registering key/object %s/%s", key, object));
        }

        logger.debug("applying processors");
        object = applyProcessors(object, metadata);
        if (object == null)
        {
            return;
        }

        registryMap.putAndLogWarningIfDuplicate(key, object);

        try
        {
            if (!hasFlag(metadata, MuleRegistry.LIFECYCLE_BYPASS_FLAG))
            {
                if(logger.isDebugEnabled())
                {
                    logger.debug("applying lifecycle to object: " + object);
                }
                getLifecycleManager().applyCompletedPhases(object);
            }
        }
        catch (MuleException e)
        {
            throw new RegistrationException(e);
        }
    }

    protected void checkDisposed() throws RegistrationException
    {
        if(getLifecycleManager().isPhaseComplete(Disposable.PHASE_NAME))
        {
            throw new RegistrationException(MessageFactory.createStaticMessage("Cannot register objects on the registry as the context is disposed"));
        }
    }

    protected boolean hasFlag(Object metaData, int flag)
    {
        return !(metaData == null || !(metaData instanceof Integer)) && ((Integer) metaData & flag) != 0;
    }

    /**
     * Will remove an object by name from the registry. By default the registry will apply all remaining lifecycle phases
     * to the object when it is removed.
     *
     * @param key the name or key of the object to remove from the registry
     * @param metadata Meta data flags supported are {@link org.mule.api.registry.MuleRegistry#LIFECYCLE_BYPASS_FLAG}
     * @throws RegistrationException if there is a problem unregistering the object. Typically this will be because
     * the object's lifecycle threw an exception
     */
    public void unregisterObject(String key, Object metadata) throws RegistrationException
    {
        Object obj = registryMap.remove(key);

        try
        {
            if (!hasFlag(metadata, MuleRegistry.LIFECYCLE_BYPASS_FLAG))
            {
                getLifecycleManager().applyPhase(obj, lifecycleManager.getCurrentPhase(), Disposable.PHASE_NAME);
            }
        }
        catch (MuleException e)
        {
            throw new RegistrationException(e);
        }

    }

    public void unregisterObject(String key) throws RegistrationException
    {
        unregisterObject(key, Object.class);
    }

    // /////////////////////////////////////////////////////////////////////////
    // Registry Metadata
    // /////////////////////////////////////////////////////////////////////////

    public boolean isReadOnly()
    {
        return false;
    }

    public boolean isRemote()
    {
        return false;
    }

    /**
     * This class encapsulates the {@link HashMap} that's used for storing the objects in the
     * transient registry and also shields client code from having to deal with locking the
     * {@link ReadWriteLock} for the exposed Map operations.
     */
    private static class RegistryMap
    {
        private final Map<String, Object> registry = new HashMap<String, Object>();
        private final ReadWriteLock registryLock = new ReentrantReadWriteLock();

        private Log logger;

        public RegistryMap(Log log)
        {
            super();
            logger = log;
        }

        public Collection<?> select(Predicate predicate)
        {
            Lock readLock = registryLock.readLock();
            try
            {
                readLock.lock();
                return CollectionUtils.select(registry.values(), predicate);
            }
            finally
            {
                readLock.unlock();
            }
        }

        public void clear()
        {
            Lock writeLock = registryLock.writeLock();
            try
            {
                writeLock.lock();
                registry.clear();
            }
            finally
            {
                writeLock.unlock();
            }
        }

        public void putAndLogWarningIfDuplicate(String key, Object object)
        {
            Lock writeLock = registryLock.writeLock();
            try
            {
                writeLock.lock();

                if (registry.containsKey(key))
                {
                    // registry.put(key, value) would overwrite a previous entity with the same name.  Is this really what we want?
                    // Not sure whether to throw an exception or log a warning here.
                    //throw new RegistrationException("TransientRegistry already contains an object named '" + key + "'.  The previous object would be overwritten.");
                    logger.warn("TransientRegistry already contains an object named '" + key + "'.  The previous object will be overwritten.");
                }
                registry.put(key, object);
            }
            finally
            {
                writeLock.unlock();
            }
        }

        public void putAll(Map<String, Object> map)
        {
            Lock writeLock = registryLock.writeLock();
            try
            {
                writeLock.lock();
                registry.putAll(map);
            }
            finally
            {
                writeLock.unlock();
            }
        }

        @SuppressWarnings("unchecked")
        public <T> T get(String key)
        {
            Lock readLock = registryLock.readLock();
            try
            {
                readLock.lock();
                return (T) registry.get(key);
            }
            finally
            {
                readLock.unlock();
            }
        }

        public Object remove(String key)
        {
            Lock writeLock = registryLock.writeLock();
            try
            {
                writeLock.lock();
                return registry.remove(key);
            }
            finally
            {
                writeLock.unlock();
            }
        }

        public Set<Entry<String, Object>> entrySet()
        {
            return registry.entrySet();
        }

        public void lockForReading()
        {
            registryLock.readLock().lock();
        }

        public void unlockForReading()
        {
            registryLock.readLock().unlock();
        }
    }
}
