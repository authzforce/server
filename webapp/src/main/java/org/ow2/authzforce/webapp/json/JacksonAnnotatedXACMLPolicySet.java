package org.ow2.authzforce.webapp.json;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.PolicySet;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.jsontype.impl.AsWrapperTypeSerializer;
import com.fasterxml.jackson.databind.jsontype.impl.ClassNameIdResolver;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.type.TypeFactory;

/**
 * 
 * Jackson Mix-in for adding Jackson annotation to XSD-generated class {@link PolicySet}. FIXME: remove this if not used in client.xml/beans.xml
 */
interface JacksonAnnotatedXACMLPolicySet
{

	public final class PolicySetCombinerInputConverter extends StdSerializer<Serializable>
	{

		protected PolicySetCombinerInputConverter()
		{
			super(Serializable.class);
		}

		private static final JavaType SERIALIZABLE_JAVA_TYPE = TypeFactory.defaultInstance().constructSimpleType(Serializable.class, new JavaType[] {});

		/**
		 * FIXME: replace with MinimalClassNameIdResolver
		 */
		private static final TypeSerializer AS_WRAPPER_TYPE_SERIALIZER = new AsWrapperTypeSerializer(new ClassNameIdResolver(SERIALIZABLE_JAVA_TYPE, TypeFactory.defaultInstance()), null);

		@Override
		public void serialize(final Serializable value, final JsonGenerator gen, final SerializerProvider provider) throws IOException
		{
			AS_WRAPPER_TYPE_SERIALIZER.writeTypePrefixForObject(value, gen);
			provider.defaultSerializeValue(value, gen);
			AS_WRAPPER_TYPE_SERIALIZER.writeTypeSuffixForObject(value, gen);
		}

	}

	@JsonDeserialize(using = ListFromJsonArrayDeserializer.class/* , contentAs = Object.class */)
	@JsonSerialize(contentUsing = PolicySetCombinerInputConverter.class)
	List<Serializable> getPolicySetsAndPoliciesAndPolicySetIdReferences();
}
