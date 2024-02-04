/*
 * Copyright (C) 2012-2024 THALES.
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

/**
 * Copyright (C) 2014 The logback-extensions developers (logback-user@qos.ch)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

/**
 * Bootstrap listener for custom Logback initialization in a web environment.
 * Delegates to WebLogbackConfigurer (see its javadoc for configuration details).
 * <p/>
 * <b>WARNING: Assumes an expanded WAR file</b>, both for loading the configuration
 * file and for writing the log files. If you want to keep your WAR unexpanded or
 * don't need application-specific log files within the WAR directory, don't use
 * Logback setup within the application (thus, don't use Log4jConfigListener or
 * LogbackConfigServlet). Instead, use a global, VM-wide Log4J setup (for example,
 * in JBoss) or JDK 1.4's <code>java.util.logging</code> (which is global too).
 * <p/>
 * This listener should be registered before ContextLoaderListener in web.xml,
 * when using custom Logback initialization.
 * <p/>
 * For Servlet 2.2 containers and Servlet 2.3 ones that do not initialize listeners before servlets, use
 * LogbackConfigServlet. See the ContextLoaderServlet javadoc for details.
 *
 * @author Juergen Hoeller
 * @author Les Hazlewood
 * @see WebLogbackConfigurer
 * @see LogbackConfigListener
 * @since 0.1
 */
public class LogbackConfigListener implements ServletContextListener
{

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        WebLogbackConfigurer.shutdownLogging(event.getServletContext());
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        WebLogbackConfigurer.initLogging(event.getServletContext());
    }
}
