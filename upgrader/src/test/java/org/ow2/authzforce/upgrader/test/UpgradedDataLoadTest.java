/**
 * Copyright (C) 2012-2016 Thales Services SAS.
 *
 * This file is part of AuthZForce CE.
 *
 * AuthZForce CE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AuthZForce CE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AuthZForce CE.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.ow2.authzforce.upgrader.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.naming.NamingException;
import javax.ws.rs.NotFoundException;

import oasis.names.tc.xacml._3_0.core.schema.wd_17.IdReferenceType;

import org.ow2.authzforce.rest.api.jaxrs.DomainPropertiesResource;
import org.ow2.authzforce.rest.api.jaxrs.DomainResource;
import org.ow2.authzforce.rest.api.jaxrs.DomainsResource;
import org.ow2.authzforce.rest.api.xmlns.AttributeProviders;
import org.ow2.authzforce.rest.api.xmlns.Domain;
import org.ow2.authzforce.rest.api.xmlns.DomainProperties;
import org.ow2.authzforce.rest.api.xmlns.ResourceContent;
import org.ow2.authzforce.rest.api.xmlns.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.jndi.SimpleNamingContextBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import org.w3._2005.atom.Link;

@ContextConfiguration(locations = { "classpath:META-INF/spring/beans.xml" })
public class UpgradedDataLoadTest extends AbstractTestNGSpringContextTests
{

	private static final Logger LOGGER = LoggerFactory.getLogger(UpgradedDataLoadTest.class);

	@Autowired
	private DomainsResource domainsResourceBean;

	private int domainExternalId = 0;
	private final Set<String> createdDomainIds = new HashSet<>();

	private DomainResource testDomain;

	private String testDomainId;

	/**
	 * Test parameters from testng.xml are ignored when executing with maven surefire plugin, so we use default values for all.
	 * 
	 * WARNING: the BeforeTest-annotated method must be in the test class, not in a super class although the same method logic is used in other test class
	 * 
	 * @param serverRootDir
	 * 
	 * @param domainSyncIntervalSec
	 * @throws Exception
	 */
	@Parameters({ "server.root.dir", "org.ow2.authzforce.domains.sync.interval", "org.ow2.authzforce.domains.enablePdpOnly" })
	@BeforeTest
	public void beforeTest(final String serverRootDir, @Optional("-1") final int domainSyncIntervalSec, @Optional("false") final boolean enablePdpOnly) throws Exception
	{
		System.out.println("Testing data in directory: " + serverRootDir);
		final File targetDir = new File("target");
		// set catalina.base property in server's logback.xml
		System.setProperty("catalina.base", targetDir.toURI().toString());

		final File confDir = new File(serverRootDir + "/conf");
		final String confURI = confDir.toURI().toString();
		final File dataDir = new File(serverRootDir + "/data");
		final String dataURI = dataDir.toURI().toString();

		// Set some server properties via JNDI
		try
		{
			final SimpleNamingContextBuilder jndiCtxFactoryBuilder = SimpleNamingContextBuilder.emptyActivatedContextBuilder();
			jndiCtxFactoryBuilder.bind("java:comp/env/org.ow2.authzforce.config.dir", confURI);
			jndiCtxFactoryBuilder.bind("java:comp/env/org.ow2.authzforce.data.dir", dataURI);
			jndiCtxFactoryBuilder.bind("java:comp/env/org.ow2.authzforce.uuid.gen.randomMulticastAddressBased", Boolean.TRUE);
			jndiCtxFactoryBuilder.bind("java:comp/env/org.ow2.authzforce.domains.sync.interval", domainSyncIntervalSec);
			jndiCtxFactoryBuilder.bind("java:comp/env/org.ow2.authzforce.domains.enablePdpOnly", enablePdpOnly);
		}
		catch (final NamingException ex)
		{
			throw new RuntimeException("Error setting property via JNDI", ex);
		}

		/*
		 * Workaround for: http://stackoverflow.com/questions/10184602/accessing -spring-context-in-testngs -beforetest https://jira.spring.io/browse/SPR-4072 https://jira.spring.io/browse/SPR-5404
		 * (duplicate of previous issue) springTestContextPrepareTestInstance() happens in
		 * 
		 * @BeforeClass before no access to Autowired beans by default in
		 * 
		 * @BeforeTest
		 */
		super.springTestContextPrepareTestInstance();
		testDomainId = domainsResourceBean.getDomains(null).getLinks().get(0).getHref();
		testDomain = domainsResourceBean.getDomainResource(testDomainId);
		// force externalId
		final DomainPropertiesResource testDomainPropsResource = testDomain.getDomainPropertiesResource();
		final DomainProperties domainProperties = new DomainProperties(testDomainPropsResource.getDomainProperties().getDescription(), "external" + Integer.toString(domainExternalId));
		domainExternalId += 1;
		testDomain.getDomainPropertiesResource().updateDomainProperties(domainProperties);
	}

	/**
	 * 
	 * WARNING: the AfterTest-annotated method must be in the test class, not in a super class although the same method logic is used in other test class
	 */
	@AfterTest
	public void deleteDomains()
	{
		for (final String domainId : createdDomainIds)
		{
			LOGGER.debug("Deleting domain ID={}", domainId);
			final DomainResource domainResource = domainsResourceBean.getDomainResource(domainId);
			final DomainProperties domainProperties = domainResource.deleteDomain();
			assertNotNull(domainProperties, String.format("Error deleting domain ID=%s", domainId));

			boolean isDeleted = false;
			try
			{
				// try to get domain again ->
				// MUST fail
				domainsResourceBean.getDomainResource(domainId);
			}
			catch (final NotFoundException nfe)
			{
				isDeleted = true;
			}

			assertTrue(isDeleted, String.format("Error deleting domain ID=%s", domainId));
		}
	}

	@Test(invocationCount = 3)
	public void addAndGetDomain()
	{
		// externalID is x:NCName therefore cannot start with a number
		final DomainProperties domainProperties = new DomainProperties("Test domain", "external" + Integer.toString(domainExternalId));
		domainExternalId += 1;
		final Link domainLink = domainsResourceBean.addDomain(domainProperties);
		assertNotNull(domainLink, "Domain creation failure");

		// The link href gives the new domain ID
		final String domainId = domainLink.getHref();
		LOGGER.debug("Added domain ID={}", domainId);
		assertTrue(createdDomainIds.add(domainId), "Domain ID uniqueness violation: Conflict on domain ID=" + domainId);

		// verify link appears on GET /domains
		final Resources domainResources = domainsResourceBean.getDomains(null);
		assertNotNull(domainResources, "No domain found");
		for (final Link link : domainResources.getLinks())
		{
			final String href = link.getHref();
			if (domainId.equals(href))
			{
				return;
			}
		}

		fail("Test domain added by 'addAndGetDomain' not found in links from getDomains");
	}

	@Test(dependsOnMethods = { "addAndGetDomain" })
	public void getDomains()
	{
		final Resources domainResources = domainsResourceBean.getDomains(null);
		assertNotNull(domainResources, "No domain found");
		// match retrieved domains against created ones in addDomain test
		int matchedDomainCount = 0;
		for (final Link domainLink : domainResources.getLinks())
		{
			final String domainId = domainLink.getHref();
			if (createdDomainIds.contains(domainId))
			{
				matchedDomainCount += 1;
			}
		}

		assertEquals(matchedDomainCount, createdDomainIds.size(), "Test domains added by 'addDomain' not all found by getDomains");
	}

	@Test
	public void getDomain()
	{
		final Domain domainResourceInfo = testDomain.getDomain();
		assertNotNull(domainResourceInfo);

		final DomainProperties props = domainResourceInfo.getProperties();
		assertNotNull(props);
	}

	@Test(dependsOnMethods = { "getDomain" })
	public void getDomainByExternalId()
	{
		final String externalId = testDomain.getDomain().getProperties().getExternalId();
		assert externalId != null;

		final List<Link> domainLinks = domainsResourceBean.getDomains(externalId).getLinks();
		// verify that there is only one domain resource link and it is the one
		// we are looking for
		assertEquals(domainLinks.size(), 1);

		final String matchedDomainId = domainLinks.get(0).getHref();
		assertEquals(matchedDomainId, testDomainId, "getDomains(externalId) returned wrong domainId: " + matchedDomainId + " instead of " + testDomainId);
	}

	@Test
	public void getDomainProperties()
	{
		final DomainPropertiesResource propsResource = testDomain.getDomainPropertiesResource();
		assertNotNull(propsResource);

		final DomainProperties props = propsResource.getDomainProperties();
		assertNotNull(props);
	}

	@Test(dependsOnMethods = { "getDomain" })
	public void getPap()
	{
		final ResourceContent papResource = testDomain.getPapResource().getPAP();
		assertNotNull(papResource);
	}

	@Test(dependsOnMethods = { "getPap" })
	public void getAttributeProviders()
	{
		final AttributeProviders attributeProviders = testDomain.getPapResource().getAttributeProvidersResource().getAttributeProviderList();
		assertNotNull(attributeProviders);
	}

	@Test(dependsOnMethods = { "getPap" })
	public void getPolicies()
	{
		final Resources resources = testDomain.getPapResource().getPoliciesResource().getPolicies();
		assertNotNull(resources);
		assertTrue(resources.getLinks().size() > 0, "No resource for root policy found");
	}

	@Test(dependsOnMethods = { "getDomainProperties", "getPolicies" })
	public void getRootPolicy()
	{
		final IdReferenceType rootPolicyRef = testDomain.getPapResource().getPdpPropertiesResource().getOtherPdpProperties().getRootPolicyRefExpression();
		final List<Link> links = testDomain.getPapResource().getPoliciesResource().getPolicyResource(rootPolicyRef.getValue()).getPolicyVersions().getLinks();
		assertTrue(links.size() > 0, "No root policy version found");
	}

}
