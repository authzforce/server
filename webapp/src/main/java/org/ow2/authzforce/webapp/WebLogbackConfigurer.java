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

import ch.qos.logback.core.joran.spi.JoranException;

import jakarta.servlet.ServletContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.util.ServletContextPropertyUtils;
import org.springframework.web.util.WebUtils;

import java.io.FileNotFoundException;
import java.lang.reflect.Method;

/**
 * Convenience class that performs custom Logback initialization for web environments,
 * allowing for log file paths within the web application.
 * <p/>
 * <b>WARNING: Assumes an expanded WAR file</b>, both for loading the configuration
 * file and for writing the log files. If you want to keep your WAR unexpanded or
 * don't need application-specific log files within the WAR directory, don't use
 * Logback setup within the application (thus, don't use LogbackConfigListener or
 * LogbackConfigServlet). Instead, use a global, VM-wide Logback setup (for example,
 * in JBoss) or JDK 1.4's <code>java.util.logging</code> (which is global too).
 * <p/>
 * Supports two init parameters at the servlet context level (that is,
 * context-param entries in web.xml):
 * <ul>
 * <li><i>"logbackConfigLocation":</i><br>
 * Location of the Logback config file; either a "classpath:" location (e.g.
 * "classpath:myLogback.xml"), an absolute file URL (e.g. "file:C:/logback.properties),
 * or a plain path relative to the web application root directory (e.g.
 * "/WEB-INF/logback.xml"). If not specified, default Logback initialization will
 * apply ("logback.xml" or "logback_test.xml" in the class path; see Logback documentation for details).
 * <li><i>"logbackExposeWebAppRoot":</i><br>
 * Whether the web app root system property should be exposed, allowing for log
 * file paths relative to the web application root directory. Default is "true";
 * specify "false" to suppress expose of the web app root system property. See
 * below for details on how to use this system property in log file locations.
 * </ul>
 * <p/>
 * Note: <code>initLogging</code> should be called before any other Spring activity
 * (when using Logback), for proper initialization before any Spring logging attempts.
 * <p/>
 * By default, this configurer automatically sets the web app root system property,
 * for "${key}" substitutions within log file locations in the Logback config file,
 * allowing for log file paths relative to the web application root directory.
 * The default system property key is "webapp.root", to be used in a Logback config
 * file like as follows:
 * <p/>
 * <code>
 * <appender name="FILE" class="ch.qos.logback.core.FileAppender">
 * <layout class="ch.qos.logback.classic.PatternLayout">
 * <pattern>%-4relative [%thread] %-5level %class - %msg%n</pattern>
 * </layout>
 * <File>${webapp.root}/WEB-INF/demo.log</File>
 * </appender>
 * </code>
 * <p/>
 * Alternatively, specify a unique context-param "webAppRootKey" per web application.
 * For example, with "webAppRootKey = "demo.root":
 * <p/>
 * <code>
 * <appender name="FILE" class="ch.qos.logback.core.FileAppender">
 * <layout class="ch.qos.logback.classic.PatternLayout">
 * <pattern>%-4relative [%thread] %-5level %class - %msg%n</pattern>
 * </layout>
 * <File>${demo.root}/WEB-INF/demo.log</File>
 * </appender>
 * </code>
 * <p/>
 * <b>WARNING:</b> Some containers (like Tomcat) do <i>not</i> keep system properties
 * separate per web app. You have to use unique "webAppRootKey" context-params per web
 * app then, to avoid clashes. Other containers like Resin do isolate each web app's
 * system properties: Here you can use the default key (i.e. no "webAppRootKey"
 * context-param at all) without worrying.
 *
 * @author Juergen Hoeller
 * @author Les Hazlewood
 * @since 0.1
 */
public class WebLogbackConfigurer {

    /**
     * Parameter specifying the location of the logback config file
     */
    public static final String CONFIG_LOCATION_PARAM = "logbackConfigLocation";
    /**
     * Parameter specifying whether to expose the web app root system property
     */
    public static final String EXPOSE_WEB_APP_ROOT_PARAM = "logbackExposeWebAppRoot";

    private WebLogbackConfigurer() {
    }

    /**
     * Initialize Logback, including setting the web app root system property.
     *
     * @param servletContext the current ServletContext
     * @see org.springframework.web.util.WebUtils#setWebAppRootSystemProperty
     */
    public static void initLogging(ServletContext servletContext) {
        // Expose the web app root system property.
        if (exposeWebAppRoot(servletContext)) {
            WebUtils.setWebAppRootSystemProperty(servletContext);
        }

        // Only perform custom Logback initialization in case of a config file.
        String locationParam = servletContext.getInitParameter(CONFIG_LOCATION_PARAM);
        if (locationParam != null) {
            // Perform Logback initialization; else rely on Logback's default initialization.
            for (String location : StringUtils.tokenizeToStringArray(locationParam,
                    ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS)) {
                try {
                    // Resolve context property placeholders before potentially resolving real path.
                    location = ServletContextPropertyUtils.resolvePlaceholders(location, servletContext);
                    // Return a URL (e.g. "classpath:" or "file:") as-is;
                    // consider a plain file path as relative to the web application root directory.
                    if (!ResourceUtils.isUrl(location)) {
                        location = WebUtils.getRealPath(servletContext, location);
                    }

                    // Write log message to server log.
                    servletContext.log("Initializing Logback from [" + location + "]");

                    // Initialize
                    LogbackConfigurer.initLogging(location);
                    break;
                } catch (FileNotFoundException ex) {
                    servletContext.log("No logback configuration file found at [" + location + "]");
                    //throw new IllegalArgumentException("Invalid 'logbackConfigLocation' parameter: " + ex.getMessage());
                } catch (JoranException e) {
                    throw new RuntimeException("Unexpected error while configuring logback", e);
                }
            }
        }

        //If SLF4J's java.util.logging bridge is available in the classpath, install it. This will direct any messages
        //from the Java Logging framework into SLF4J. When logging is terminated, the bridge will need to be uninstalled
        try {
            Class<?> julBridge = ClassUtils.forName("org.slf4j.bridge.SLF4JBridgeHandler", ClassUtils.getDefaultClassLoader());

            Method removeHandlers = ReflectionUtils.findMethod(julBridge, "removeHandlersForRootLogger");
            if (removeHandlers != null) {
                servletContext.log("Removing all previous handlers for JUL to SLF4J bridge");
                ReflectionUtils.invokeMethod(removeHandlers, null);
            }

            Method install = ReflectionUtils.findMethod(julBridge, "install");
            if (install != null) {
                servletContext.log("Installing JUL to SLF4J bridge");
                ReflectionUtils.invokeMethod(install, null);
            }
        } catch (ClassNotFoundException ignored) {
            //Indicates the java.util.logging bridge is not in the classpath. This is not an indication of a problem.
            servletContext.log("JUL to SLF4J bridge is not available on the classpath");
        }
    }

    /**
     * Shut down Logback, properly releasing all file locks
     * and resetting the web app root system property.
     *
     * @param servletContext the current ServletContext
     * @see WebUtils#removeWebAppRootSystemProperty
     */
    public static void shutdownLogging(ServletContext servletContext) {
        //Uninstall the SLF4J java.util.logging bridge *before* shutting down the Logback framework.
        try {
            Class<?> julBridge = ClassUtils.forName("org.slf4j.bridge.SLF4JBridgeHandler", ClassUtils.getDefaultClassLoader());
            Method uninstall = ReflectionUtils.findMethod(julBridge, "uninstall");
            if (uninstall != null) {
                servletContext.log("Uninstalling JUL to SLF4J bridge");
                ReflectionUtils.invokeMethod(uninstall, null);
            }
        } catch (ClassNotFoundException ignored) {
            //No need to shutdown the java.util.logging bridge. If it's not on the classpath, it wasn't started either.
        }

        try {
            servletContext.log("Shutting down Logback");
            LogbackConfigurer.shutdownLogging();
        } finally {
            // Remove the web app root system property.
            if (exposeWebAppRoot(servletContext)) {
                WebUtils.removeWebAppRootSystemProperty(servletContext);
            }
        }
    }

    /**
     * Return whether to expose the web app root system property,
     * checking the corresponding ServletContext init parameter.
     *
     * @param servletContext the servlet context
     * @return {@code true} if the webapp's root should be exposed; otherwise, {@code false}
     * @see #EXPOSE_WEB_APP_ROOT_PARAM
     */
    @SuppressWarnings({"BooleanMethodNameMustStartWithQuestion"})
    private static boolean exposeWebAppRoot(ServletContext servletContext) {
        String exposeWebAppRootParam = servletContext.getInitParameter(EXPOSE_WEB_APP_ROOT_PARAM);
        return exposeWebAppRootParam == null || Boolean.valueOf(exposeWebAppRootParam);
    }
}

