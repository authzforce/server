package org.ow2.authzforce.webapp.json;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.http.converter.json.Jackson2ObjectMapperFactoryBean;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;

/**
 * FIXME: delete this class. Jackson {@link ObjectMapper} {@link FactoryBean} for Spring configuration
 *
 */
public class JacksonObjectMapperFactoryBean extends Jackson2ObjectMapperFactoryBean
{

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.springframework.http.converter.json.Jackson2ObjectMapperFactoryBean#setDefaultTyping(com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder)
	 */
	@Override
	public void setDefaultTyping(final TypeResolverBuilder<?> typeResolverBuilder)
	{
		super.setDefaultTyping(typeResolverBuilder);
		/*
		 * From ObjectMapper#enableDefaultTypeing(...): TypeResolverBuilder<?> typer = new DefaultTypeResolverBuilder(applicability); // we'll always use full class name, when using defaulting
		 */
		// we'll always use full class name, when using defaulting
		typeResolverBuilder.init(JsonTypeInfo.Id.CLASS, null);
		typeResolverBuilder.inclusion(JsonTypeInfo.As.WRAPPER_OBJECT);
	}

}
