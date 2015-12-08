/**
 * Copyright (C) 2012-2015 Thales Services SAS.
 *
 * This file is part of AuthZForce.
 *
 * AuthZForce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AuthZForce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AuthZForce.  If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * 
 */
package com.thalesgroup.authzforce.rest;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotNull;
import javax.ws.rs.InternalServerErrorException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;

import org.apache.commons.io.FileUtils;

import com.sun.xacml.PDP;
import com.sun.xacml.finder.AttributeFinder;
import com.sun.xacml.finder.AttributeFinderModule;
import com.thalesgroup.appsec.util.Utils;
import com.thalesgroup.authz.model._3.AttributeFinders;
import com.thalesgroup.authz.model._3.PolicySets;
import com.thalesgroup.authz.model._3_0.resource.Properties;
import com.thalesgroup.authz.model.ext._3.AbstractAttributeFinder;
import com.thalesgroup.authz.model.ext._3.AbstractPolicyFinder;
import com.thalesgroup.authzforce.core.PdpConfigurationParser;
import com.thalesgroup.authzforce.core.PdpModelHandler;
import com.thalesgroup.authzforce.core.XACMLBindingUtils;
import com.thalesgroup.authzforce.pdp.model._2015._06.BaseStaticPolicyFinder;
import com.thalesgroup.authzforce.pdp.model._2015._06.Pdp;

import net.sf.saxon.lib.Logger;
import oasis.names.tc.xacml._3_0.core.schema.wd_17.PolicySet;

public class SecurityDomain
{
	/**
	 * name of domain properties file
	 */
	public static final String DOMAIN_PROPERTIES_FILENAME = "properties.xml";

	/**
	 * Comment in domain properties file
	 */
	public static final String DOMAIN_PROPERTIES_COMMENT = "Domain Properties";

	/**
	 * Name of domain name property
	 */
	public static final String DOMAIN_NAME_PROPERTY = "name";

	/**
	 * Name of domain description property
	 */
	public static final String DOMAIN_DESCRIPTION_PROPERTY = "description";

	/**
	 * Name of domain policy file
	 */
	public static final String DOMAIN_POLICYSET_FILENAME = "policySet.xml";

	/**
	 * Name of domain policy backup file
	 */
	public static final String DOMAIN_POLICYSET_BACKUP_FILENAME = "policySet.xml.old";

	/**
	 * Name of file containing list of candidate PolicySets for <PolicySetIdReference>s used in
	 * {@link #DOMAIN_POLICYSET_FILENAME}
	 */
	public static final String DOMAIN_REF_POLICYSET_FILENAME = "refPolicySets.xml";

	/**
	 * Name of backup file of candidate PolicySets for <PolicySetIdReference>s used in
	 * {@link #DOMAIN_POLICYSET_FILENAME}
	 */
	private static final String DOMAIN_REF_POLICYSET_BACKUP_FILENAME = "refPolicySets.xml.old";

	/**
	 * Name of domain PDP attribute finders file
	 */
	public static final String DOMAIN_ATTRIBUTE_FINDERS_FILENAME = "attributeFinders.xml";

	/**
	 * Name of domain PDP attribute finders backup file
	 */
	public static final String DOMAIN_ATTRIBUTE_FINDERS_BACKUP_FILENAME = "attributeFinders.xml.old";

	/**
	 * Name of PDP configuration file
	 */
	public static final String DOMAIN_PDP_CONFIG_FILENAME = "pdp.xml";

	/**
	 * The output encoding to use when marshalling the XML data of domain configuration files:
	 * properties, policySet, etc.
	 */
	public static final String UTF8_JAXB_ENCODING = "UTF-8";

	// private static final Logger LOGGER = LoggerFactory.getLogger(SecurityDomain.class);

	private final JAXBContext jaxbCtx;

	// private final File domainDir;

	private final File propFile;

	private final Schema authzApiSchema;

	private final File policySetFile;

	private final File policySetBackupFile;

	private final File attrFindersFile;

	private final File attrFindersBackupFile;

	private final File pdpConfFile;

	private PDP pdp;

//	private final List<AbstractPolicyFinder> defaultPolicyFinderModules;
//
//	private final List<AbstractAttributeFinder> defaultAttributeFinderModules;

	private final File refPolicySetFile;

	private final File refPolicySetBackupFile;

	/**
	 * Constructs end-user policy admin domain
	 * 
	 * @param domainDir
	 *            domain directory
	 * @param jaxbCtx
	 *            JAXB context for marshalling/unmarshalling configuration data
	 * @param authzApiSchema
	 * @param pdpModelHandler PDP configuration model handler
	 * @param props
	 *            new domain properties for new domain creation, null for if domain data (including
	 *            properties) already exist
	 * @throws JAXBException
	 * 			Invalid configuration
	 * @throws IOException 
	 * 			Problem finding configuration file
	 */
	public SecurityDomain(@NotNull File domainDir, @NotNull JAXBContext jaxbCtx, @NotNull Schema authzApiSchema, @NotNull PdpModelHandler pdpModelHandler, Properties props) throws IOException, JAXBException
	{
		this.jaxbCtx = jaxbCtx;
		this.authzApiSchema = authzApiSchema;
		// domainDir
		Utils.checkFile("'domainDir' arg file", domainDir, true, true);
		// this.domainDir = domainDir;

		// propFile
		this.propFile = new File(domainDir, DOMAIN_PROPERTIES_FILENAME);
		if (props == null)
		{
			// Use properties in existing properties file
			Utils.checkFile("Domain properties file", propFile, false, true);
		} else
		{
			// set/save new properties
			this.setProperties(props);
		}

		// Check policy file
		this.policySetFile = new File(domainDir, DOMAIN_POLICYSET_FILENAME);
		Utils.checkFile("Domain PolicySet file", policySetFile, false, true);

		this.policySetBackupFile = new File(domainDir, DOMAIN_POLICYSET_BACKUP_FILENAME);

		// Check candidate policySets for <PolicySetIdReference>s
		this.refPolicySetFile = new File(domainDir, DOMAIN_REF_POLICYSET_FILENAME);
		Utils.checkFile("Domain refPolicySet file", refPolicySetFile, false, true);

		this.refPolicySetBackupFile = new File(domainDir, DOMAIN_REF_POLICYSET_BACKUP_FILENAME);

		// Check attributeFinders configuration file
		this.attrFindersFile = new File(domainDir, DOMAIN_ATTRIBUTE_FINDERS_FILENAME);
		Utils.checkFile("Domain PDP Attribute Finders file", policySetFile, false, true);

		this.attrFindersBackupFile = new File(domainDir, DOMAIN_ATTRIBUTE_FINDERS_BACKUP_FILENAME);

		// Check PDP config file
		this.pdpConfFile = new File(domainDir, DOMAIN_PDP_CONFIG_FILENAME);
		Utils.checkFile("Domain PDP configuration file", pdpConfFile, false, true);

		// Initialize PDP
		final String pdpConfLocation = pdpConfFile.getAbsolutePath();
		
		BaseStaticPolicyFinder jaxbRootPolicyFinder = new BaseStaticPolicyFinder();
		jaxbRootPolicyFinder.setPolicyLocation(this.policySetFile.getPath());
		
		final Pdp jaxbPDP = new Pdp();
		jaxbPDP.setRootPolicyFinder(jaxbRootPolicyFinder);
		this.pdp = PdpConfigurationParser.getPDP(pdpConfLocation);
	}

	private void updatePDP() throws IOException, JAXBException
	{
		Utils.checkFile("Domain PDP configuration file", pdpConfFile, false, true);
		this.pdp = PdpConfigurationParser.getPDP(pdpConfFile.getAbsolutePath());
	}
	/**
	 * Update PDP
	 * 
	 * @param reloadPolicyFinderModules
	 *            true if and only if Policies must be reloaded (after update). Root PolicySet file
	 *            is loaded with {@link StaticPolicyFinderModule} and added to PolicyFinder in
	 *            addition to {@link #defaultPolicyFinderModules}; refPolicySets are loaded with
	 *            {@link StaticDomRefPolicyFinderModule} and added to PolicyFinder in addition to
	 *            {@link #defaultPolicyFinderModules} and previous {@link StaticPolicyFinderModule}.
	 * @param attrfinders
	 *            Extra attribute finders added to {@link #defaultAttributeFinderModules} if not
	 *            null
	 * @throws JAXBException
	 * 			Invalid configuration
	 * @throws IOException 
	 * 			Problem finding configuration file
	 * @deprecated 
	 * 			Use updatePDP() instead
	 */
	private void updatePDP(boolean reloadPolicyFinderModules, AttributeFinders attrfinders) throws IOException, JAXBException
	{
		updatePDP();
		
//		if (reloadPolicyFinderModules)
//		{
//			final List<AbstractPolicyFinder> policyFinderModules = new ArrayList<>(this.defaultPolicyFinderModules);
//
//			// StaticDomRefPolicyFinderModule
//			final AbstractPolicyFinder refPolicyFinderMod;
//			final PolicySets policySets = this.getRefPolicySets();
//			final List<PolicySet> policySetList = policySets.getPolicySets();
//			refPolicyFinderMod = new StaticRefPolicyFinderModule(policySetList.toArray(new String[policySetList.size()]));
//			policyFinderModules.add(refPolicyFinderMod);
//
//			// StaticPolicyFinderModule
//			final AbstractPolicyFinder rootPolicyFinderMod = new StaticPolicyFinderModule( new String[] {this.policySetFile
//					.getAbsolutePath()});
//			policyFinderModules.add(rootPolicyFinderMod);
//
//			final AbstractPolicyFinder policyFinder = this.pdp.getPolicyFinder();
//			policyFinder.setModules(policyFinderModules);
//			/**
//			 * Finder Modules' init methods must be called after PolicyFinder#setModules() and in
//			 * order of dependency (e.g. StaticPolicyFinderModule depends on
//			 * StaticDomRefPolicyFinderModule to resolve policy reference, therefore initialized
//			 * after the latter); so that a finder module can find/check policies resolved by other
//			 * modules on which it depends, or already resolved by itself during initialization,
//			 * using the policyFinder.
//			 * 
//			 */
//			refPolicyFinderMod.init(policyFinder);
//			rootPolicyFinderMod.init(policyFinder);
//		}
//
//		if (attrfinders != null)
//		{
//			final List<AbstractPolicyFinder> attrFinderModules = new ArrayList<>(this.defaultAttributeFinderModules);
//			for (AbstractAttributeFinder attrFinderConf : attrfinders.getAttributeFinders())
//			{
//				final AbstractPolicyFinder newAttrFinderModule = PdpExtensionFactory.getInstance(attrFinderConf);
//				attrFinderModules.add(newAttrFinderModule);
//			}
//
//			final AttributeFinder attrFinder = this.pdp.getAttributeFinder();
//			attrFinder.setModules(attrFinderModules);
//		}
	}
	
	/**
	 * Reload PDP from file (policy repository)
	 * @throws JAXBException
	 * 			Invalid configuration
	 * @throws IOException 
	 * 			Problem finding configuration file 
	 */
	public void reloadPDP() throws IOException, JAXBException {
		updatePDP();
	}

	/**
	 * Get domain properties
	 * 
	 * @return domain properties
	 */
	public Properties getProperties()
	{
		final Unmarshaller unmarshaller;
		try
		{
			unmarshaller = jaxbCtx.createUnmarshaller();
			unmarshaller.setSchema(authzApiSchema);
			final JAXBElement<Properties> jaxbElt = unmarshaller.unmarshal(new StreamSource(propFile), Properties.class);
			return jaxbElt.getValue();
		} catch (JAXBException e)
		{
			throw new InternalServerErrorException(new RuntimeException("Error unmarshalling domain properties from file: "
					+ this.propFile.getAbsolutePath(), e));
		}
	}

	/**
	 * Set domain properties
	 * 
	 * @param properties
	 *            domain properties
	 */
	public void setProperties(Properties properties)
	{
		final Marshaller marshaller;
		try
		{
			marshaller = jaxbCtx.createMarshaller();
			marshaller.setSchema(authzApiSchema);
			marshaller.setProperty(Marshaller.JAXB_ENCODING, UTF8_JAXB_ENCODING);
			marshaller.marshal(properties, propFile);
		} catch (JAXBException e)
		{
			throw new InternalServerErrorException(new RuntimeException("Error marshalling domain properties to file: "
					+ this.propFile.getAbsolutePath(), e));
		}
	}

	/**
	 * Get domain policy
	 * 
	 * @return domain policy
	 */
	public PolicySet getPolicySet()
	{
		final Unmarshaller unmarshaller;
		try
		{
			unmarshaller = XACMLBindingUtils.createXacml3Unmarshaller();
			unmarshaller.setSchema(this.authzApiSchema);
			final JAXBElement<PolicySet> jaxbElt = unmarshaller.unmarshal(new StreamSource(policySetFile), PolicySet.class);
			return jaxbElt.getValue();
		} catch (JAXBException e)
		{
			throw new InternalServerErrorException(new RuntimeException("Error unmarshalling domain policy from file: "
					+ this.policySetFile.getAbsolutePath(), e));
		}
	}

	/**
	 * Set domain policy
	 * 
	 * @param policySet
	 *            domain policy
	 * @throws IOException
	 *             error deleting previous domain policy backup file if any
	 * @throws JAXBException
	 *             error marshalling new policySet to file for persistence
	 */
	public void setPolicySet(PolicySet policySet) throws IOException, JAXBException
	{
		// before changing policy, backup current policy
		FileUtils.copyFile(this.policySetFile, this.policySetBackupFile);
		final Marshaller marshaller;
		try
		{
			marshaller = XACMLBindingUtils.createXacml3Marshaller();
			marshaller.setSchema(authzApiSchema);
			marshaller.setProperty(Marshaller.JAXB_ENCODING, UTF8_JAXB_ENCODING);
			marshaller.marshal(policySet, policySetFile);
		} catch (JAXBException e)
		{
			// Replace back with backup in case the file is corrupted due to this exception
			FileUtils.copyFile(this.policySetBackupFile, this.policySetFile);
			throw new JAXBException("Error marshalling new domain policy to file: " + this.policySetFile.getAbsolutePath(), e);
		}

		/*
		 * Try updating PDP with new policy
		 * If any error occurs, reject the operation and restore previous state.
		 */
		try
		{
			// TODO: optimization: load policy directly from PolicySet arg (requires changing
			// Sunxacml StaticPolicyFinderModule code)
			updatePDP(true, null);
		} catch (Throwable e)
		{
			FileUtils.copyFile(this.policySetBackupFile, this.policySetFile);
			if (e instanceof IllegalArgumentException) {
				throw e;
			}
			
			throw new IllegalArgumentException("PolicySet rejected by PDP because of unsupported or illegal parameters or internal error", e);
		}
	}

	/**
	 * Returns the PDP enforcing the domain policy
	 * 
	 * @return domain PDP
	 */
	public PDP getPDP()
	{
		return this.pdp;
	}

	/**
	 * Get domain PDP attribute finders
	 * 
	 * @return attribute finders
	 */
	public AttributeFinders getAttributeFinders()
	{
		final Unmarshaller unmarshaller;
		try
		{
			unmarshaller = jaxbCtx.createUnmarshaller();
			unmarshaller.setSchema(authzApiSchema);
			final JAXBElement<AttributeFinders> jaxbElt = unmarshaller.unmarshal(new StreamSource(attrFindersFile), AttributeFinders.class);
			return jaxbElt.getValue();
		} catch (JAXBException e)
		{
			throw new InternalServerErrorException(new RuntimeException("Error unmarshalling domain PDP attribute finders from file: "
					+ this.attrFindersFile.getAbsolutePath(), e));
		}
	}

	/**
	 * Set domain PDP attribute finders
	 * 
	 * @param attributefinders
	 *            attribute finders
	 * @throws IOException
	 *             error deleting previous domain policy backup file if any
	 * @throws JAXBException
	 *             error marshalling new policySet to file for persistence
	 */
	public void setAttributeFinders(AttributeFinders attributefinders) throws IOException, JAXBException
	{
		// before changing attribute finders, backup current ones
		FileUtils.copyFile(this.attrFindersFile, this.attrFindersBackupFile);
		final Marshaller marshaller;
		try
		{
			marshaller = jaxbCtx.createMarshaller();
			marshaller.setSchema(authzApiSchema);
			marshaller.setProperty(Marshaller.JAXB_ENCODING, UTF8_JAXB_ENCODING);
			marshaller.marshal(attributefinders, attrFindersFile);
		} catch (JAXBException e)
		{
			// Replace back with backup in case the file is corrupted due to this exception
			FileUtils.copyFile(this.attrFindersBackupFile, this.attrFindersFile);
			throw new JAXBException("Error marshalling new domain PDP attribute finders to file: " + this.attrFindersFile.getAbsolutePath(), e);
		}

		/* Try updating PDP with new attribute finders
		 * If any error occurs, reject the operation and restore previous state
		 */
		try
		{
			updatePDP(false, attributefinders);
		} catch (Throwable e)
		{
			FileUtils.copyFile(this.attrFindersBackupFile, this.attrFindersFile);
			throw new IllegalArgumentException("Attribute finders configuration rejected by PDP because of unsupported or illegal parameters", e);
		}
	}

	/**
	 * Get candidate <PolicySet>s to be referred to by <PolicySetIdReference>s in domain root
	 * <PolicySet> set with {@link #setPolicySet(PolicySet)}
	 * 
	 * @return candidate PolicySets
	 */
	public PolicySets getRefPolicySets()
	{
		final Unmarshaller unmarshaller;
		try
		{
			unmarshaller = XACMLBindingUtils.createXacml3Unmarshaller();
			unmarshaller.setSchema(authzApiSchema);
			final JAXBElement<PolicySets> jaxbElt = unmarshaller.unmarshal(new StreamSource(this.refPolicySetFile), PolicySets.class);
			return jaxbElt.getValue();
		} catch (JAXBException e)
		{
			throw new InternalServerErrorException(new RuntimeException("Error unmarshalling domain ref-PolicySets from file: "
					+ this.refPolicySetFile.getAbsolutePath(), e));
		}
	}

	/**
	 * Updates candidate <PolicySet>s to be referred to by <PolicySetIdReference>s in domain root
	 * <PolicySet> set with {@link #setPolicySet(PolicySet)}
	 * 
	 * @param policysets
	 * @throws IOException
	 *             if persistence of the <PolicySet>s to file failed
	 * @throws JAXBException
	 *             if marshalling of the <PolicySet>s to XML file for persistence failed
	 */
	public void setRefPolicySets(PolicySets policysets) throws IOException, JAXBException
	{
		// before changing ref-PolicySets, backup current ones
		FileUtils.copyFile(this.refPolicySetFile, this.refPolicySetBackupFile);
		final Marshaller marshaller;
		try
		{
			marshaller = XACMLBindingUtils.createXacml3Marshaller();
			marshaller.setSchema(authzApiSchema);
			marshaller.setProperty(Marshaller.JAXB_ENCODING, UTF8_JAXB_ENCODING);
			marshaller.marshal(policysets, this.refPolicySetFile);
		} catch (JAXBException e)
		{
			// Replace back with backup in case the file is corrupted due to this exception
			FileUtils.copyFile(this.refPolicySetBackupFile, this.refPolicySetFile);
			throw new JAXBException("Error marshalling new domain ref-PolicySets to file: " + this.refPolicySetFile.getAbsolutePath(), e);
		}

		/*
		 * Try updating PDP with new ref-PolicySets
		 * If any error occurs, reject the operation and restore previous state.
		 */
		try
		{
			updatePDP(true, null);
		} catch (Throwable e)
		{
			FileUtils.copyFile(this.refPolicySetBackupFile, this.refPolicySetFile);
			throw new IllegalArgumentException("Ref-PolicySets rejected by PDP because of unsupported or illegal parameters", e);
		}
	}
}
