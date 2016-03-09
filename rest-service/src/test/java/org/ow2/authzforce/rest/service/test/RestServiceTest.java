/**
 * Copyright (C) 2015-2015 Thales Services SAS. All rights reserved. No warranty, explicit or implicit, provided.
 */
package org.ow2.authzforce.rest.service.test;

/**
 *
 *
 */
import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Random;
import java.util.TimeZone;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.PolicySet;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Request;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Target;

import org.apache.cxf.endpoint.Server;
import org.apache.cxf.interceptor.FIStaxInInterceptor;
import org.apache.cxf.interceptor.FIStaxOutInterceptor;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.ClientConfiguration;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.provider.JAXBElementProvider;
import org.apache.cxf.jaxrs.utils.schemas.SchemaHandler;
import org.ow2.authzforce.core.pdp.impl.PdpModelHandler;
import org.ow2.authzforce.core.xmlns.test.TestAttributeProvider;
import org.ow2.authzforce.pap.dao.flatfile.FlatFileDAOUtils;
import org.ow2.authzforce.pap.dao.flatfile.xmlns.DomainProperties;
import org.ow2.authzforce.rest.api.jaxrs.DomainsResource;
import org.ow2.authzforce.rest.api.xmlns.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.ITestContext;

@ContextConfiguration(locations = { "classpath:META-INF/spring/applicationContext.xml" })
abstract class RestServiceTest extends AbstractTestNGSpringContextTests
{
	private static final Logger LOGGER = LoggerFactory.getLogger(RestServiceTest.class);

	/**
	 * Test context attribute set by the beforeSuite() to the initialized value of class member 'client'. To be reused
	 * in other test classes of the same test suite. Attribute value type: {@link DomainsResource}
	 */
	protected static final String REST_CLIENT_TEST_CONTEXT_ATTRIBUTE_ID = "org.ow2.authzforce.test.rest.client";

	protected final static String REST_APP_BASE_URL = "org.ow2.authzforce.test.rest.app.base.url";

	/**
	 * Test context attribute set by the beforeSuite() to the initialized value of class member 'pdpModelHandler'. To be
	 * reused in other test classes of the same test suite. Attribute value type: {@link PdpModelHandler}
	 */
	protected static final String PDP_MODEL_HANDLER_TEST_CONTEXT_ATTRIBUTE_ID = "org.ow2.authzforce.test.pdp.model.handler";

	/**
	 * Test context attribute set by the beforeSuite() to the XML schema of all XML data sent/received by the API
	 * client. To be reused in other test classes of the same test suite. Attribute value type: {@link Schema}
	 */
	protected static final String REST_CLIENT_API_SCHEMA_TEST_CONTEXT_ATTRIBUTE_ID = "org.ow2.authzforce.test.rest.client.api.schema";

	private static final String COMPONENT_ENV_JNDI_CTX_NAME = "java:comp/env";

	protected static final int MAX_XML_TEXT_LENGTH = 1000;

	protected static final File DOMAINS_DIR = new File("target/server.data/domains");

	protected static final File XACML_SAMPLES_DIR = new File("src/test/resources/xacml.samples");
	static
	{
		if (!XACML_SAMPLES_DIR.exists())
		{
			throw new RuntimeException("XACML SAMPLES DIRECTORY NOT FOUND: " + XACML_SAMPLES_DIR);
		}
	}

	static final File XACML_IIIG301_PDP_TEST_DIR = new File(RestServiceTest.XACML_SAMPLES_DIR, "IIIG301");
	static
	{
		if (!XACML_IIIG301_PDP_TEST_DIR.exists())
		{
			throw new RuntimeException("XACML PDP TEST DIRECTORY NOT FOUND: " + XACML_IIIG301_PDP_TEST_DIR);
		}
	}

	static final File XACML_POLICYREFS_PDP_TEST_DIR = new File(RestServiceTest.XACML_SAMPLES_DIR,
			"pdp/PolicyReference.Valid");
	static
	{
		if (!XACML_POLICYREFS_PDP_TEST_DIR.exists())
		{
			throw new RuntimeException("XACML POLICYREFS PDP TEST DIRECTORY NOT FOUND: "
					+ XACML_POLICYREFS_PDP_TEST_DIR);
		}
	}

	protected static final Path SAMPLE_DOMAIN_DIR = Paths
			.get("src/test/resources/domain.samples/A0bdIbmGEeWhFwcKrC9gSQ");
	static
	{
		if (!Files.exists(SAMPLE_DOMAIN_DIR))
		{
			throw new RuntimeException("SAMPLE DOMAIN DIRECTORY NOT FOUND: " + SAMPLE_DOMAIN_DIR);
		}
	}

	/*
	 * JAXB context for (un)marshalling XACML
	 */
	protected static final JAXBContext JAXB_CTX;

	static
	{
		try
		{
			JAXB_CTX = JAXBContext.newInstance(PolicySet.class, Request.class, Resources.class, DomainProperties.class,
					TestAttributeProvider.class);
		} catch (JAXBException e)
		{
			throw new RuntimeException("Error instantiating JAXB context for XML to Java binding", e);
		}
	}

	protected static final Random PRNG = new Random();

	protected static final DateFormat UTC_DATE_WITH_MILLIS_FORMATTER = new SimpleDateFormat(
			"yyyy-MM-dd HH:mm:ss.SSS ('UTC')");
	static
	{
		UTC_DATE_WITH_MILLIS_FORMATTER.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	/**
	 * XACML request filename
	 */
	protected final static String REQUEST_FILENAME = "request.xml";

	/**
	 * XACML policy filename used by default when no PDP configuration file found, i.e. no file named "pdp.xml" exists
	 * in the test directory
	 */
	protected final static String TEST_POLICY_FILENAME = "policy.xml";

	/**
	 * Expected XACML response filename
	 */
	protected final static String EXPECTED_RESPONSE_FILENAME = "response.xml";

	protected static final String TEST_REF_POLICIES_DIRECTORY_NAME = "refPolicies";

	protected static final String TEST_ATTRIBUTE_PROVIDER_FILENAME = "attributeProvider.xml";

	protected static final String TEST_DEFAULT_POLICYSET_FILENAME = "policy.xml";

	public final static String DOMAIN_POLICIES_DIRNAME = "policies";
	public final static String DOMAIN_PDP_CONF_FILENAME = "pdp.xml";

	protected static final String FASTINFOSET_MEDIA_TYPE = "application/fastinfoset";

	public final static String DOMAIN_PROPERTIES_FILENAME = "properties.xml";

	protected static PolicySet createDumbPolicySet(String policyId, String version)
	{
		return new PolicySet(null, null, null, new Target(null), null, null, null, policyId, version,
				"urn:oasis:names:tc:xacml:3.0:policy-combining-algorithm:deny-unless-permit", BigInteger.ZERO);
	}

	@Autowired
	@Qualifier("tazService")
	private JAXRSServerFactoryBean jaxrsServerFactoryBean;

	@Autowired
	@Qualifier("pdpModelHandler")
	protected PdpModelHandler pdpModelHandler;

	@Autowired
	@Qualifier("clientApiSchemaHandler")
	protected SchemaHandler clientApiSchemaHandler;

	private Server server = null;

	@Autowired
	@Qualifier("clientJaxbProvider")
	private JAXBElementProvider<?> clientJaxbProvider;

	@Autowired
	@Qualifier("clientJaxbProviderFI")
	private JAXBElementProvider<?> clientJaxbProviderFI;

	protected Unmarshaller unmarshaller = null;

	protected DomainsResource domainsAPIProxyClient = null;

	protected void startServerAndInitCLient(String appBaseUrl, int maxPolicyCountPerDomain,
			int maxVersionCountPerPolicy, boolean removeOldVersionsTooMany, int domainSyncIntervalSec,
			ITestContext testCtx) throws Exception
	{

		if (appBaseUrl == null)
		{
			// Not a remote server -> start the JAX-RS server locally with all his configuration (including base URL)
			// locally
			// configured in META-INF/spring/server.xml on the classpath
			/*
			 * Make sure the directory target/domains exists and is empty (see
			 * src/test/resources/META-INF/spring/server.xml for actual domains directory path)
			 */
			if (DOMAINS_DIR.exists())
			{
				// delete to start clean
				FlatFileDAOUtils.deleteDirectory(DOMAINS_DIR.toPath(), 4);
			}

			DOMAINS_DIR.mkdirs();

			// Override some server properties via JNDI
			try
			{
				// Create initial context
				System.setProperty(Context.INITIAL_CONTEXT_FACTORY, "org.eclipse.jetty.jndi.InitialContextFactory");
				System.setProperty(Context.URL_PKG_PREFIXES, "org.eclipse.jetty.jndi");

				InitialContext ic = new InitialContext();
				// check if the comp/env context already exists (created with previous test)
				try
				{
					ic.list(COMPONENT_ENV_JNDI_CTX_NAME);
				} catch (NameNotFoundException e)
				{
					ic.createSubcontext(COMPONENT_ENV_JNDI_CTX_NAME);
				}

				ic.rebind(COMPONENT_ENV_JNDI_CTX_NAME + "/org.ow2.authzforce.domain.maxPolicyCount",
						maxPolicyCountPerDomain);
				ic.rebind(COMPONENT_ENV_JNDI_CTX_NAME + "/org.ow2.authzforce.domain.policy.maxVersionCount",
						maxVersionCountPerPolicy);
				ic.rebind(COMPONENT_ENV_JNDI_CTX_NAME + "/org.ow2.authzforce.domain.policy.removeOldVersionsIfTooMany",
						removeOldVersionsTooMany);
				ic.rebind(COMPONENT_ENV_JNDI_CTX_NAME + "/org.ow2.authzforce.domains.sync.interval",
						domainSyncIntervalSec);
			} catch (NamingException ex)
			{
				throw new RuntimeException("Error setting property via JNDI", ex);
			}

			// For SSL debugging
			// System.setProperty("javax.net.debug", "all");

			/*
			 * Workaround for: http://stackoverflow.com/questions/10184602/accessing -spring-context-in-testngs
			 * -beforetest https://jira.spring.io/browse/SPR-4072 https://jira.spring.io/browse/SPR-5404 (duplicate of
			 * previous issue) springTestContextPrepareTestInstance() happens in
			 * 
			 * @BeforeClass before no access to Autowired beans by default in
			 * 
			 * @BeforeTest
			 */
			super.springTestContextPrepareTestInstance();
			// Server started by Spring
			server = jaxrsServerFactoryBean.getServer();

			testCtx.setAttribute(PDP_MODEL_HANDLER_TEST_CONTEXT_ATTRIBUTE_ID, pdpModelHandler);
		}

		/**
		 * Create the REST (JAX-RS) client
		 */
		/*
		 * WARNING: if tests are to be multi-threaded, modify according to Thread-safety section of CXF JAX-RS client
		 * API documentation http://cxf .apache.org/docs/jax-rs-client-api.html#JAX-RSClientAPI-ThreadSafety
		 */
		final String serverBaseAddress = appBaseUrl == null ? jaxrsServerFactoryBean.getAddress() : appBaseUrl;
		testCtx.setAttribute(REST_APP_BASE_URL, serverBaseAddress);

		/*
		 * Use FASTINFOSET-aware client if FastInfoset enabled More info on testing FastInfoSet with CXF:
		 * https://github.
		 * com/apache/cxf/blob/a0f0667ad6ef136ed32707d361732617bc152c2e/systests/jaxrs/src/test/java/org/apache
		 * /cxf/systest/jaxrs/JAXRSSoapBookTest.java WARNING: "application/fastinfoset" mediatype must be declared
		 * before others for this to work (in WADL or Consumes annotations); if not (with CXF 3.1.0), the first
		 * mediatype is set as Content-type, which causes exception on server-side such as:
		 * com.ctc.wstx.exc.WstxIOException: Invalid UTF-8 middle byte 0x0 (at char #0, byte #-1)
		 */
		// FIXME: defined enableFastInfoset test parameter to decide which client to use
		 domainsAPIProxyClient = JAXRSClientFactory.create(serverBaseAddress, DomainsResource.class,
		 Collections.singletonList(clientJaxbProvider));
//		domainsAPIProxyClient = JAXRSClientFactory.create(serverBaseAddress, DomainsResourceFastInfoset.class,
//				Collections.singletonList(clientJaxbProviderFI));

//			checkFiInterceptors(WebClient.getConfig(domainsAPIProxyClient));
		testCtx.setAttribute(REST_CLIENT_TEST_CONTEXT_ATTRIBUTE_ID, domainsAPIProxyClient);

		/**
		 * Request/response logging (for debugging).
		 */
		if (LOGGER.isDebugEnabled())
		{
			final ClientConfiguration proxyClientConf = WebClient.getConfig(domainsAPIProxyClient);
			proxyClientConf.getInInterceptors().add(new LoggingInInterceptor());
			proxyClientConf.getOutInterceptors().add(new LoggingOutInterceptor());
		}

		// Unmarshaller
		final Schema apiSchema = this.clientApiSchemaHandler.getSchema();
		testCtx.setAttribute(REST_CLIENT_API_SCHEMA_TEST_CONTEXT_ATTRIBUTE_ID, apiSchema);

		unmarshaller = JAXB_CTX.createUnmarshaller();
		unmarshaller.setSchema(apiSchema);
	}

	private void checkFiInterceptors(ClientConfiguration cfg)
	{
		int count = 0;
		for (Interceptor<?> in : cfg.getInInterceptors())
		{
			if (in instanceof FIStaxInInterceptor)
			{
				count++;
				break;
			}
		}
		for (Interceptor<?> in : cfg.getOutInterceptors())
		{
			if (in instanceof FIStaxOutInterceptor)
			{
				count++;
				break;
			}
		}
		if (count != 2)
		{
			throw new RuntimeException("In and Out FastInfoset interceptors are expected");
		}
	}

	protected void destroyServer() throws Exception
	{
		// server != null only if test suite property start.server = true
		if (server != null)
		{
			server.stop();
			server.destroy();
		}
	}

}
