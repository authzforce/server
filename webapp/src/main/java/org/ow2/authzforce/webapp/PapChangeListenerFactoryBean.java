/*
 * Copyright (C) 2012-2026 THALES.
 *
 * This file is part of AuthzForce CE.
 *
 * AuthzForce CE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AuthzForce CE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AuthzForce CE.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.ow2.authzforce.webapp;

import org.ow2.authzforce.rest.service.PapChangeListener;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.ClassUtils;

/**
 * Creates an optional listener using the web application's classloader.
 * Empty configuration produces a null listener; invalid configuration fails startup.
 * Listeners must expose a public no-argument constructor.
 */
public final class PapChangeListenerFactoryBean
        implements FactoryBean<PapChangeListener>, BeanClassLoaderAware, InitializingBean, DisposableBean
{
    private final String className;
    private ClassLoader classLoader = ClassUtils.getDefaultClassLoader();
    private PapChangeListener listener;

    /**
     * Configure the optional extension class.
     * @param className fully qualified listener class name, or empty to disable notifications
     */
    public PapChangeListenerFactoryBean(String className)
    {
        this.className = className == null ? "" : className.trim();
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader)
    {
        this.classLoader = classLoader;
    }

    @Override
    public void afterPropertiesSet() throws Exception
    {
        if (!className.isEmpty())
        {
            final Class<? extends PapChangeListener> type =
                    ClassUtils.forName(className, classLoader).asSubclass(PapChangeListener.class);
            listener = BeanUtils.instantiateClass(type.getConstructor());
        }
    }

    @Override
    public PapChangeListener getObject()
    {
        return listener;
    }

    @Override
    public Class<?> getObjectType()
    {
        return PapChangeListener.class;
    }

    @Override
    public boolean isSingleton()
    {
        return true;
    }

    @Override
    public void destroy() throws Exception
    {
        if (listener instanceof AutoCloseable closeable) closeable.close();
    }
}

