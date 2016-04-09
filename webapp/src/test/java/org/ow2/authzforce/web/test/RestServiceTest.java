/**
 * Copyright (C) 2015-2015 Thales Services SAS. All rights reserved. No warranty, explicit or implicit, provided.
 */
package org.ow2.authzforce.web.test;

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

import javax.ws.rs.core.MediaType;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.deploy.ContextEnvironment;
import org.apache.catalina.deploy.NamingResources;
import org.apache.catalina.startup.Tomcat;
import org.apache.cxf.interceptor.FIStaxInInterceptor;
import org.apache.cxf.interceptor.FIStaxOutInterceptor;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.jaxrs.client.ClientConfiguration;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.provider.JAXBElementProvider;
import org.apache.cxf.jaxrs.utils.schemas.SchemaHandler;
import org.apache.cxf.message.Message;
import org.ow2.authzforce.core.pdp.impl.PdpModelHandler;
import org.ow2.authzforce.core.xmlns.test.TestAttributeProvider;
import org.ow2.authzforce.pap.dao.flatfile.FlatFileDAOUtils;
import org.ow2.authzforce.pap.dao.flatfile.xmlns.DomainProperties;
import org.ow2.authzforce.rest.api.jaxrs.DomainsResource;
import org.ow2.authzforce.rest.api.xmlns.Resources;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.ITestContext;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.PolicySet;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Request;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Target;

@ContextConfiguration(locations = { "classpath:META-INF/spring/client.xml" })
abstract class RestServiceTest extends AbstractTestNGSpringContextTests
{
	private static final int EMBEDDED_SERVER_PORT = 9080;
	private static final String EMBEDDED_APP_CONTEXT_PATH = "/";
	private static final String EMBEDDED_APP_BASE_URL = "http://127.0.0.1:" + EMBEDDED_SERVER_PORT + EMBEDDED_APP_CONTEXT_PATH;

	private static volatile boolean IS_EMBEDDED_SERVER_STARTED = false;

	protected static final int MAX_XML_TEXT_LENGTH = 1000;

	// For maxAttributeSize = 500 in JAXRS server configuration, exception raised only when chars.length >
	// 910! WHY? Possible issue with woodstox library.
	// FIXME: report this issue to CXF/Woodstox
	protected static final int MAX_XML_ATTRIBUTE_SIZE = 910;

	protected static final File DOMAINS_DIR = new File("target/server/conf/authzforce-ce/domains");

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

	static final File XACML_POLICYREFS_PDP_TEST_DIR = new File(RestServiceTest.XACML_SAMPLES_DIR, "pdp/PolicyReference.Valid");
	static
	{
		if (!XACML_POLICYREFS_PDP_TEST_DIR.exists())
		{
			throw new RuntimeException("XACML POLICYREFS PDP TEST DIRECTORY NOT FOUND: " + XACML_POLICYREFS_PDP_TEST_DIR);
		}
	}

	protected static final Path SAMPLE_DOMAIN_DIR = Paths.get("src/test/resources/domain.samples/A0bdIbmGEeWhFwcKrC9gSQ");
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
			JAXB_CTX = JAXBContext.newInstance(PolicySet.class, Request.class, Resources.class, DomainProperties.class, TestAttributeProvider.class);
		} catch (JAXBException e)
		{
			throw new RuntimeException("Error instantiating JAXB context for XML to Java binding", e);
		}
	}

	protected static final Random PRNG = new Random();

	protected static final DateFormat UTC_DATE_WITH_MILLIS_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS ('UTC')");
	static
	{
		UTC_DATE_WITH_MILLIS_FORMATTER.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	/**
	 * XACML request filename
	 */
	protected final static String REQUEST_FILENAME = "request.xml";

	/**
	 * XACML policy filename used by default when no PDP configuration file found, i.e. no file named "pdp.xml" exists in the test directory
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
		return createDumbPolicySet(policyId, version, null);
	}

	protected static PolicySet createDumbPolicySet(String policyId, String version, String description)
	{
		return new PolicySet(description, null, null, new Target(null), null, null, null, policyId, version, "urn:oasis:names:tc:xacml:3.0:policy-combining-algorithm:deny-unless-permit", BigInteger.ZERO);
	}

	@Autowired
	@Qualifier("pdpModelHandler")
	protected PdpModelHandler pdpModelHandler;

	@Autowired
	@Qualifier("clientApiSchemaHandler")
	protected SchemaHandler clientApiSchemaHandler;

	private Tomcat server = null;

	@Autowired
	@Qualifier("clientJaxbProvider")
	private JAXBElementProvider<?> clientJaxbProvider;

	@Autowired
	@Qualifier("clientJaxbProviderFI")
	private JAXBElementProvider<?> clientJaxbProviderFI;

	protected Unmarshaller unmarshaller = null;

	protected DomainsResource domainsAPIProxyClient = null;

	protected void startServerAndInitCLient(String remoteAppBaseUrl, boolean enableFastInfoset, int domainSyncIntervalSec, ITestContext testCtx) throws Exception
	{
		// remoteAppBaseUrl undefined means server/app is started locally (embedded)
		if ((remoteAppBaseUrl == null || remoteAppBaseUrl.isEmpty()) && !IS_EMBEDDED_SERVER_STARTED)
		{
			// Not a remote server -> start the embedded server (local)
			/*
			 * Make sure the domains directory exists and is empty
			 */
			if (DOMAINS_DIR.exists())
			{
				// delete to start clean
				FlatFileDAOUtils.deleteDirectory(DOMAINS_DIR.toPath(), 4);
			}

			DOMAINS_DIR.mkdirs();

			// For SSL debugging
			// System.setProperty("javax.net.debug", "all");

			// FIXME: in xacml schema, remove schemaLocation="http://www.w3.org/2001/xml.xsd" to avoid accessExternalSchema error restricting protocols
			// WORKAROUND
			System.setProperty("javax.xml.accessExternalSchema", "all");

			// Create Jetty Server
			// set 'catalina.base' for Tomcat work dir and property in server's logback.xml
			System.setProperty("catalina.base", new File("target/server").getAbsolutePath());

			// Initialize embedded Tomcat server
			server = new Tomcat();
			server.setPort(EMBEDDED_SERVER_PORT);
			// enable JNDI
			server.enableNaming();
			final Context webappCtx = server.addWebapp(EMBEDDED_APP_CONTEXT_PATH, new File("src/main/webapp").getAbsolutePath());
			for (final LifecycleListener listener : webappCtx.findLifecycleListeners())
			{
				if (listener instanceof Tomcat.DefaultWebXmlListener)
				{
					webappCtx.removeLifecycleListener(listener);
				}
			}

			// Initialize server JNDI properties for embedded Tomcat
			final NamingResources webappNamingResources = webappCtx.getNamingResources();

			// override env-entry for domains sync interval
			ContextEnvironment syncIntervalEnv = new ContextEnvironment();
			syncIntervalEnv.setName("org.ow2.authzforce.domains.sync.interval");
			syncIntervalEnv.setType("java.lang.Integer");
			syncIntervalEnv.setValue(Integer.toString(domainSyncIntervalSec));
			syncIntervalEnv.setOverride(false);
			webappNamingResources.addEnvironment(syncIntervalEnv);

			/*
			 * Override spring active profile context parameter with system property (no way to override context parameter with Tomcat embedded API, otherwise
			 * error "Duplicate context initialization parameter")
			 */
			System.setProperty("spring.profiles.active", (enableFastInfoset ? "+" : "-") + "fastinfoset");

			server.start();
			IS_EMBEDDED_SERVER_STARTED = true;
		}

		/**
		 * Create the REST (JAX-RS) client
		 */
		// initialize client properties from Spring
		/*
		 * Workaround for: http://stackoverflow.com/questions/10184602/accessing -spring-context-in-testngs -beforetest https://jira.spring.io/browse/SPR-4072
		 * https://jira.spring.io/browse/SPR-5404 (duplicate of previous issue) springTestContextPrepareTestInstance() happens in
		 * 
		 * @BeforeClass before no access to Autowired beans by default in
		 * 
		 * @BeforeTest
		 */
		super.springTestContextPrepareTestInstance();
		/*
		 * WARNING: if tests are to be multi-threaded, modify according to Thread-safety section of CXF JAX-RS client API documentation http://cxf
		 * .apache.org/docs/jax-rs-client-api.html#JAX-RSClientAPI-ThreadSafety
		 */
		final String serverBaseAddress = remoteAppBaseUrl == null || remoteAppBaseUrl.isEmpty() ? EMBEDDED_APP_BASE_URL : remoteAppBaseUrl;
		final ClientConfiguration proxyClientConf;
		if (enableFastInfoset)
		{
			/*
			 * Use FASTINFOSET-aware client if FastInfoset enabled. More info on testing FastInfoSet with CXF: https://github.
			 * com/apache/cxf/blob/a0f0667ad6ef136ed32707d361732617bc152c2e/systests/jaxrs/src/test/java/org/apache /cxf/systest/jaxrs/JAXRSSoapBookTest.java
			 * WARNING: "application/fastinfoset" mediatype must be declared before others for this to work (in WADL or Consumes annotations); if not (with CXF
			 * 3.1.0), the first mediatype is set as Content-type, which causes exception on server-side such as: com.ctc.wstx.exc.WstxIOException: Invalid
			 * UTF-8 middle byte 0x0 (at char #0, byte #-1)
			 */
			domainsAPIProxyClient = JAXRSClientFactory.create(serverBaseAddress, DomainsResourceFastInfoset.class, Collections.singletonList(clientJaxbProviderFI));
			proxyClientConf = WebClient.getConfig(domainsAPIProxyClient);
			checkFiInterceptors(proxyClientConf);
		} else
		{
			domainsAPIProxyClient = JAXRSClientFactory.create(serverBaseAddress, DomainsResource.class, Collections.singletonList(clientJaxbProvider));
			proxyClientConf = WebClient.getConfig(domainsAPIProxyClient);
			// if no fastinfoset, force to use only application/xml mediatype:
			proxyClientConf.getRequestContext().put(Message.CONTENT_TYPE, MediaType.APPLICATION_XML);
			proxyClientConf.getOutInterceptors().add(new ContentTypeHeaderModifier());
		}

		/**
		 * Request/response logging (for debugging).
		 */
		// if (LOGGER.isDebugEnabled()) {
		proxyClientConf.getInInterceptors().add(new LoggingInInterceptor());
		proxyClientConf.getOutInterceptors().add(new LoggingOutInterceptor());
		proxyClientConf.getHttpConduit().getClient().setConnectionTimeout(0);
		proxyClientConf.getHttpConduit().getClient().setReceiveTimeout(0);
		// }

		// Unmarshaller
		final Schema apiSchema = this.clientApiSchemaHandler.getSchema();

		unmarshaller = JAXB_CTX.createUnmarshaller();
		unmarshaller.setSchema(apiSchema);
	}

	protected void shutdownServer() throws Exception
	{
		// server != null only if test suite property start.server = true
		if (server != null)
		{
			server.stop();
			server.destroy();
			IS_EMBEDDED_SERVER_STARTED = false;
		}
	}

	private static void checkFiInterceptors(ClientConfiguration cfg)
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

}
