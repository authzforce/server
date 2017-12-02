/**
 * Copyright (C) 2012-2017 Thales Services SAS.
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
package org.ow2.authzforce.webapp.test;

/**
 *
 *
 */
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletException;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.Policy;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.PolicySet;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.Target;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
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
import org.apache.tomcat.util.descriptor.web.ContextEnvironment;
import org.apache.tomcat.util.descriptor.web.NamingResources;
import org.ow2.authzforce.core.pdp.impl.PdpModelHandler;
import org.ow2.authzforce.core.pdp.testutil.ext.xmlns.TestAttributeProvider;
import org.ow2.authzforce.pap.dao.flatfile.FlatFileDAOUtils;
import org.ow2.authzforce.pap.dao.flatfile.xmlns.DomainProperties;
import org.ow2.authzforce.rest.api.jaxrs.DomainsResource;
import org.ow2.authzforce.rest.api.jaxrs.ProductMetadataResource;
import org.ow2.authzforce.rest.api.xmlns.Resources;
import org.ow2.authzforce.webapp.org.apache.cxf.jaxrs.provider.json.JSONProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;

@ContextConfiguration(locations = { "classpath:META-INF/spring/client.xml" })
abstract class RestServiceTest extends AbstractTestNGSpringContextTests
{
	protected static final Random PRNG = new Random();

	/*
	 * Start embedded server on a random port between 9000 and 9999 (inclusive) to avoid conflict with another parallel test run
	 */
	private static final AtomicInteger EMBEDDED_SERVER_PORT = new AtomicInteger(9000 + PRNG.nextInt(1000));
	private static final String EMBEDDED_APP_CONTEXT_PATH = ""; // ROOT context path

	protected static final AtomicBoolean IS_EMBEDDED_SERVER_STARTED = new AtomicBoolean(false);

	private static final int XML_MAX_CHILD_ELEMENTS = 20;

	/*
	 * Below that value getWADL() test fails because WADL depth too big
	 */
	private static final int XML_MAX_ELEMENT_DEPTH = 15;
	private static final int XML_MAX_ATTRIBUTE_COUNT = 100;
	protected static final int XML_MAX_TEXT_LENGTH = 1000;

	/*
	 * For maxAttributeSize = 500 in JAXRS server configuration, exception raised only when chars.length > 911! WHY? Possible issue with woodstox library.
	 * 
	 * FIXME: report this issue to CXF/Woodstox
	 */
	private static final int XML_MAX_ATTRIBUTE_SIZE = 500;
	protected static final int XML_MAX_ATTRIBUTE_SIZE_EFFECTIVE = 911;

	protected static final File DOMAINS_DIR = new File("target/tomcat/authzforce-ce-server/data/domains");

	private static final MediaType FASTINFOSET_MEDIA_TYPE = new MediaType("application", "fastinfoset");

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

	protected static final Path SAMPLE_DOMAIN_DIR = Paths.get("src/test/resources/authzforce-ce-server/data/domains/A0bdIbmGEeWhFwcKrC9gSQ");
	static
	{
		if (!Files.exists(SAMPLE_DOMAIN_DIR))
		{
			throw new RuntimeException("SAMPLE DOMAIN DIRECTORY NOT FOUND: " + SAMPLE_DOMAIN_DIR);
		}
	}

	protected static final String SAMPLE_DOMAIN_ID = SAMPLE_DOMAIN_DIR.getFileName().toString();
	protected static final Path SAMPLE_DOMAIN_COPY_DIR = new File(DOMAINS_DIR, SAMPLE_DOMAIN_ID).toPath();

	/*
	 * JAXB context for (un)marshalling XACML
	 */
	protected static final JAXBContext JAXB_CTX;

	static
	{
		try
		{
			JAXB_CTX = JAXBContext.newInstance(Resources.class, DomainProperties.class, TestAttributeProvider.class);
		}
		catch (final JAXBException e)
		{
			throw new RuntimeException("Error instantiating JAXB context for XML to Java binding", e);
		}
	}

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
	 * Root XACML policy filename used by default when no PDP configuration file found, i.e. no file named "pdp.xml" exists in the test directory
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

	public final static String DOMAIN_PROPERTIES_FILENAME = "properties.xml";

	private static final Logger LOGGER = LoggerFactory.getLogger(RestServiceTest.class);

	protected static PolicySet createDumbXacmlPolicySet(final String policyId, final String version)
	{
		return createDumbXacmlPolicySet(policyId, version, null);
	}

	protected static PolicySet createDumbXacmlPolicySet(final String policyId, final String version, final String description)
	{
		return new PolicySet(description, null, null, new Target(null), null, null, null, policyId, version, "urn:oasis:names:tc:xacml:3.0:policy-combining-algorithm:deny-unless-permit", null);
	}

	protected static Policy createDumbXacmlPolicy(final String policyId, final String version)
	{
		return new Policy(null, null, null, new Target(null), null, null, null, policyId, version, "urn:oasis:names:tc:xacml:3.0:rule-combining-algorithm:deny-unless-permit", null);
	}

	@Autowired
	@Qualifier("clientJsonJaxbProvider")
	private JSONProvider<?> clientJsonJaxbProvider;

	@Autowired
	@Qualifier("pdpModelHandler")
	protected PdpModelHandler pdpModelHandler;

	@Autowired
	@Qualifier("clientApiSchemaHandler")
	protected SchemaHandler clientApiSchemaHandler;

	private Tomcat embeddedServer = null;

	@Autowired
	@Qualifier("clientJaxbProvider")
	private JAXBElementProvider<?> clientJaxbProvider;

	@Autowired
	@Qualifier("clientJaxbProviderFI")
	private JAXBElementProvider<?> clientJaxbProviderFI;

	protected Unmarshaller unmarshaller = null;

	protected DomainsResource domainsAPIProxyClient = null;
	protected ProductMetadataResource prodMetadataResClient = null;

	private static ContextEnvironment newJndiEnvEntry(final String name, final Class<?> type, final String value)
	{
		final ContextEnvironment env = new ContextEnvironment();
		env.setName(name);
		env.setType(type.getName());
		env.setValue(value);
		env.setOverride(false);
		return env;
	}

	private static Tomcat startServer(final int port, final boolean enableFastInfoset, final boolean enableDoSMitigation, final int domainSyncIntervalSec, final boolean enablePdpOnly,
			final boolean addSampleDomain) throws ServletException, IllegalArgumentException, IOException, LifecycleException
	{
		/*
		 * Make sure the domains directory exists and is empty
		 */
		if (DOMAINS_DIR.exists())
		{
			// delete to start clean
			FlatFileDAOUtils.deleteDirectory(DOMAINS_DIR.toPath(), 4);
		}

		DOMAINS_DIR.mkdirs();

		if (addSampleDomain)
		{
			// Add sample domain directory
			// create domain directory on disk
			FlatFileDAOUtils.copyDirectory(SAMPLE_DOMAIN_DIR, SAMPLE_DOMAIN_COPY_DIR, 3);
			LOGGER.info("Added sample domain: '{}'", SAMPLE_DOMAIN_ID);
		}

		// For SSL debugging
		// System.setProperty("javax.net.debug", "all");

		/*
		 * WORKAROUND to avoid accessExternalSchema error restricting protocols such as http://... in xacml schema: schemaLocation="http://www.w3.org/2001/xml.xsd"
		 */
		System.setProperty("javax.xml.accessExternalSchema", "all");

		// Create Jetty Server
		// set 'catalina.base' for Tomcat work dir and property in server's logback.xml
		System.setProperty("catalina.base", new File("target/tomcat").getAbsolutePath());

		// Initialize embedded Tomcat server
		final Tomcat embeddedServer = new Tomcat();
		/*
		 * Increment server port after getting current value, to prepare for next server tests and avoid conflict with this one
		 */
		embeddedServer.setPort(port < 0 ? EMBEDDED_SERVER_PORT.incrementAndGet() : port);
		// enable JNDI
		embeddedServer.enableNaming();
		final Context webappCtx = embeddedServer.addWebapp(EMBEDDED_APP_CONTEXT_PATH, new File("src/main/webapp").getAbsolutePath());
		for (final LifecycleListener listener : webappCtx.findLifecycleListeners())
		{
			if (listener instanceof Tomcat.DefaultWebXmlListener)
			{
				webappCtx.removeLifecycleListener(listener);
			}
		}

		// Initialize server JNDI properties for embedded Tomcat
		final NamingResources webappNamingResources = webappCtx.getNamingResources();

		/*
		 * Override spring active profile context parameter with system property (no way to override context parameter with Tomcat embedded API, otherwise error
		 * "Duplicate context initialization parameter") spring.profiles.active may be set either via servletConfig init param or servletContext init param or JNDI property
		 * java:comp/env/spring.profiles.active or system property
		 */
		webappNamingResources.addEnvironment(newJndiEnvEntry("spring.profiles.active", String.class, (enableFastInfoset ? "+" : "-") + "fastinfoset"));

		// override env-entry for domains sync interval
		webappNamingResources.addEnvironment(newJndiEnvEntry("org.ow2.authzforce.domains.sync.interval", Integer.class, Integer.toString(domainSyncIntervalSec)));

		// override env-entry for enablePdpOnly
		webappNamingResources.addEnvironment(newJndiEnvEntry("org.ow2.authzforce.domains.enablePdpOnly", Boolean.class, Boolean.toString(enablePdpOnly)));

		// override env-entry for enableXacmlJsonProfile
		webappNamingResources.addEnvironment(newJndiEnvEntry("org.ow2.authzforce.domains.enableXacmlJsonProfile", Boolean.class, Boolean.toString(!enableFastInfoset)));

		webappNamingResources.addEnvironment(newJndiEnvEntry("org.ow2.authzforce.webapp.jsonKeysToXmlAttributes", String.class, ""));
		webappNamingResources.addEnvironment(newJndiEnvEntry("org.ow2.authzforce.webapp.xmlAttributesToJsonLikeElements", Boolean.class, Boolean.FALSE.toString()));
		webappNamingResources.addEnvironment(newJndiEnvEntry("org.ow2.authzforce.webapp.jsonKeysWithArrays", String.class, ""));

		if (enableDoSMitigation)
		{
			// Override Anti-XML/JSON-DoS properties
			webappNamingResources.addEnvironment(newJndiEnvEntry("org.apache.cxf.stax.maxChildElements", Integer.class, Integer.toString(XML_MAX_CHILD_ELEMENTS)));
			webappNamingResources.addEnvironment(newJndiEnvEntry("org.apache.cxf.stax.maxElementDepth", Integer.class, Integer.toString(XML_MAX_ELEMENT_DEPTH)));

			if (!enableFastInfoset)
			{
				webappNamingResources.addEnvironment(newJndiEnvEntry("org.apache.cxf.stax.maxAttributeCount", Integer.class, Integer.toString(XML_MAX_ATTRIBUTE_COUNT)));
				webappNamingResources.addEnvironment(newJndiEnvEntry("org.apache.cxf.stax.maxAttributeSize", Integer.class, Integer.toString(XML_MAX_ATTRIBUTE_SIZE)));
				webappNamingResources.addEnvironment(newJndiEnvEntry("org.apache.cxf.stax.maxTextLength", Integer.class, Integer.toString(XML_MAX_TEXT_LENGTH)));
			}
		}

		embeddedServer.start();
		return embeddedServer;
	}

	protected enum ClientType
	{
		XML, FAST_INFOSET, JSON
	}

	protected void startServerAndInitCLient(final String remoteAppBaseUrl, final ClientType clientType, final boolean enableDoSMitigation, final int domainSyncIntervalSec, final boolean enablePdpOnly)
			throws Exception
	{
		/*
		 * If embedded server not started and remoteAppBaseUrl null/empty (i.e. server/app to be started locally (embedded))
		 */
		if (!IS_EMBEDDED_SERVER_STARTED.get() && (remoteAppBaseUrl == null || remoteAppBaseUrl.isEmpty()))
		{
			// Not a remote server -> start the embedded server (local)
			embeddedServer = startServer(-1, clientType == ClientType.FAST_INFOSET, enableDoSMitigation, domainSyncIntervalSec, enablePdpOnly, false);
			IS_EMBEDDED_SERVER_STARTED.set(true);
		}

		/**
		 * Create the REST (JAX-RS) client
		 */
		// initialize client properties from Spring
		/*
		 * Workaround for: http://stackoverflow.com/questions/10184602/accessing -spring-context-in-testngs -beforetest https://jira.spring.io/browse/SPR-4072 https://jira.spring.io/browse/SPR-5404
		 * (duplicate of previous issue) springTestContextPrepareTestInstance() happens in
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
		final String serverBaseAddress;
		/*
		 * Test if server is running embedded or not, i.e. remoteAppBaseUrl null/empty -> server embedded.
		 * 
		 * NB: member 'embeddedServer' may be null for this instance if embedded server was started by another class in the same Test; so embeddedServer == null does not mean that no embedded server
		 * is started
		 */
		if (remoteAppBaseUrl == null || remoteAppBaseUrl.isEmpty())
		{
			/*
			 * Server is a local/embedded one
			 */
			serverBaseAddress = "http://127.0.0.1:" + EMBEDDED_SERVER_PORT.get() + EMBEDDED_APP_CONTEXT_PATH;
		}
		else
		{
			serverBaseAddress = remoteAppBaseUrl;
		}

		final MediaType clientFixedContentMediaType;
		switch (clientType)
		{
			case XML:
				domainsAPIProxyClient = JAXRSClientFactory.create(serverBaseAddress, DomainsResource.class, Collections.singletonList(clientJaxbProvider));
				prodMetadataResClient = JAXRSClientFactory.create(serverBaseAddress, ProductMetadataResource.class, Collections.singletonList(clientJaxbProvider));

				/*
				 * WARNING: XmlMediaTypeHeaderSetter forces Accept header to be "application/xml" only; else if Accept "application/fastinfoset" sent as well, the server returns fastinfoset which
				 * causes error on this client-side since not supported
				 */
				clientFixedContentMediaType = MediaType.APPLICATION_XML_TYPE;
				break;

			case FAST_INFOSET:
				/*
				 * Use FASTINFOSET-aware client if FastInfoset enabled. More info on testing FastInfoSet with CXF: https://github.
				 * com/apache/cxf/blob/a0f0667ad6ef136ed32707d361732617bc152c2e/systests/jaxrs/src/test/java/org/apache /cxf/systest/jaxrs/JAXRSSoapBookTest.java.
				 */
				domainsAPIProxyClient = JAXRSClientFactory.create(serverBaseAddress, DomainsResourceFastInfoset.class, Collections.singletonList(clientJaxbProviderFI));
				prodMetadataResClient = JAXRSClientFactory.create(serverBaseAddress, ProductMetadataResource.class, Collections.singletonList(clientJaxbProviderFI));
				/*
				 * WARNING: MediaTypeHeaderSetter forces Content-type header to be "application/fastinfoset"; if not (with CXF 3.1.0), the first mediatype declared in WADL, i.e. Consume annotation of
				 * the service class ("application/xml") is set as Content-type, which causes exception on server-side such as: com.ctc.wstx.exc.WstxIOException: Invalid UTF-8 middle byte 0x0 (at char
				 * #0, byte #-1)
				 */
				clientFixedContentMediaType = FASTINFOSET_MEDIA_TYPE;
				checkFiInterceptors(WebClient.getConfig(domainsAPIProxyClient));
				break;

			case JSON:
				domainsAPIProxyClient = JAXRSClientFactory.create(serverBaseAddress, DomainsResource.class, Collections.singletonList(clientJsonJaxbProvider));
				prodMetadataResClient = JAXRSClientFactory.create(serverBaseAddress, ProductMetadataResource.class, Collections.singletonList(clientJsonJaxbProvider));
				/*
				 * WARNING: MediaTypeHeaderSetter forces Content-type header to be "application/json"
				 */
				clientFixedContentMediaType = MediaType.APPLICATION_JSON_TYPE;
				// Line below will set Content-Type: application/json even if method is GET
				// proxyClientConf.getHttpConduit().getClient().setContentType(MediaType.APPLICATION_JSON);
				break;

			default:
				throw new RuntimeException("Invalid client type: not one of: " + Arrays.toString(ClientType.values()));
		}

		final ClientConfiguration[] proxyClientConfs = { WebClient.getConfig(domainsAPIProxyClient), WebClient.getConfig(prodMetadataResClient) };
		Arrays.stream(proxyClientConfs).forEach(clientConf -> {
			clientConf.getOutInterceptors().add(new MediaTypeHeaderSetter(clientFixedContentMediaType));
			clientConf.getHttpConduit().getClient().setAccept(clientFixedContentMediaType.toString());
			/**
			 * Request/response logging (for debugging).
			 */
			// if (LOGGER.isDebugEnabled()) {
				clientConf.getInInterceptors().add(new LoggingInInterceptor());
				clientConf.getOutInterceptors().add(new LoggingOutInterceptor());
				clientConf.getHttpConduit().getClient().setConnectionTimeout(0);
				clientConf.getHttpConduit().getClient().setReceiveTimeout(0);
				// }
			});

		// Unmarshaller
		final Schema apiSchema = this.clientApiSchemaHandler.getSchema();

		unmarshaller = JAXB_CTX.createUnmarshaller();
		unmarshaller.setSchema(apiSchema);
	}

	protected void shutdownServer() throws Exception
	{
		// check whether server actually running
		if (embeddedServer != null && IS_EMBEDDED_SERVER_STARTED.get())
		{
			// Some tests may call tomcat.destroy(), some tests may just call
			// tomcat.stop(), some not call either method. Make sure that stop()
			// & destroy() are called as necessary.
			if (embeddedServer.getServer() != null && embeddedServer.getServer().getState() != LifecycleState.DESTROYED)
			{
				if (embeddedServer.getServer().getState() != LifecycleState.STOPPED)
				{
					embeddedServer.stop();
				}
				embeddedServer.destroy();
			}

			IS_EMBEDDED_SERVER_STARTED.set(false);
		}
	}

	private static void showUsage()
	{
		System.out.println("Usage using default parameter values: java RestServiceTest");
		System.out.println("Usage for non-default values: java RestServiceTest [port enableFastInfoset domainsSyncIntervalSec enablePdpOnly]");
		System.out.println("- port: (integer) server port, dynamically allocated if negative. Default: 8080.");
		System.out.println("- enableFastInfoset: (true|false) whether to enable FastInfoset support (true) or not (false). Default: false.");
		System.out.println("- domainsSyncIntervalSec: (integer) domains sync interval (seconds), disabled if negative. Default: -1");
		System.out.println("- enablePdpOnly: (true|false) whether to enable PDP features only, i.e. disable PAP/admin features. Default: false.");
	}

	public static void main(final String... args) throws IllegalArgumentException, ServletException, IOException, LifecycleException
	{
		final int port;
		final boolean enableFastInfoset;
		final boolean enableDoSMitigation;
		final int domainSyncIntervalSec;
		final boolean enablePdpOnly;
		if (args.length == 0)
		{
			port = 8080;
			enableFastInfoset = false;
			enableDoSMitigation = false;
			domainSyncIntervalSec = -1;
			enablePdpOnly = false;
		}
		else if (args.length != 5)
		{
			showUsage();
			throw new IllegalArgumentException("Invalid number of args. Expected: 0 (using defaults) or 5");
		}
		else
		{
			try
			{
				port = Integer.parseInt(args[0], 10);
				enableFastInfoset = Boolean.valueOf(args[1]);
				enableDoSMitigation = Boolean.valueOf(args[2]);
				domainSyncIntervalSec = Integer.parseInt(args[3], 10);
				enablePdpOnly = Boolean.valueOf(args[4]);
			}
			catch (final Exception e)
			{
				showUsage();
				throw new IllegalArgumentException("Invalid args. Expected args: port enableFastInfoset domainsSyncIntervalSec enablePdpOnly");
			}
		}

		final Tomcat tomcat = startServer(port, enableFastInfoset, enableDoSMitigation, domainSyncIntervalSec, enablePdpOnly, true);
		System.out.println("Server up and listening!");
		tomcat.getServer().await();
	}

	private static void checkFiInterceptors(final ClientConfiguration cfg)
	{
		int count = 0;
		for (final Interceptor<?> in : cfg.getInInterceptors())
		{
			if (in instanceof FIStaxInInterceptor)
			{
				count++;
				break;
			}
		}
		for (final Interceptor<?> in : cfg.getOutInterceptors())
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
