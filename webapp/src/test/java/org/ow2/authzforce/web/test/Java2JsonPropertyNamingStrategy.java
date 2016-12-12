package org.ow2.authzforce.web.test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

public final class Java2JsonPropertyNamingStrategy extends PropertyNamingStrategy
{
	private static final Logger LOGGER = LoggerFactory.getLogger(Java2JsonPropertyNamingStrategy.class);

	private static final IllegalArgumentException ILLEGAL_ARGUMENT_EXCEPTION = new IllegalArgumentException("invalid JAVA-to-JSON property map");
	private final Table<Class<?>, String, String> java2jsonPropertyMap = HashBasedTable.create();

	/**
	 * @param java2jsonPropertyMap
	 *            map of (key=JAVA_class:field, value=JSON_property)
	 */
	public Java2JsonPropertyNamingStrategy(final Map<String, String> java2jsonPropertyMap) throws ClassNotFoundException, NoSuchFieldException, SecurityException
	{
		for (final Entry<String, String> java2jsonProp : java2jsonPropertyMap.entrySet())
		{
			final String[] classAndField = java2jsonProp.getKey().split(":", 2);
			if (classAndField.length < 2)
			{
				throw ILLEGAL_ARGUMENT_EXCEPTION;
			}

			final Class<?> clazz = Class.forName(classAndField[0]);
			final String fieldName = classAndField[1];
			final Field field = clazz.getDeclaredField(fieldName);
			this.java2jsonPropertyMap.put(clazz, field.getName(), java2jsonProp.getValue());
		}

	}

	@Override
	public String nameForField(final MapperConfig<?> config, final AnnotatedField jacksonField, final String defaultName)
	{
		/*
		 * returning null will tell Jackson to use same name for Java and Json propery
		 */
		LOGGER.debug("Field = {}; defaultName = {}", jacksonField, defaultName);
		return java2jsonPropertyMap.get(jacksonField.getDeclaringClass(), defaultName);
	}

	@Override
	public String nameForSetterMethod(final MapperConfig<?> config, final AnnotatedMethod jacksonMethod, final String defaultName)
	{
		/*
		 * returning null will tell Jackson to use same name for Java and Json propery
		 */
		LOGGER.debug("Method = {}; defaultName = {}", jacksonMethod, defaultName);
		return java2jsonPropertyMap.get(jacksonMethod.getDeclaringClass(), defaultName);
	}

}
