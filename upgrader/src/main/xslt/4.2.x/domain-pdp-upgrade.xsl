<?xml version="1.0" encoding="UTF-8"?>
<!--

    Copyright (C) 2012-2016 Thales Services SAS.

    This file is part of AuthZForce CE.

    AuthZForce CE is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    AuthZForce CE is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with AuthZForce CE.  If not, see <http://www.gnu.org/licenses/>.

-->
<!-- PDP configuration upgrade XSL Sheet: 4.2.0 -> 5.1.x and above. To be used with Saxon XSLT processor. Author: Cyril DANGERVILLE. -->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:oldapi="http://thalesgroup.com/authz/model/3.0" xmlns:oldext="http://thalesgroup.com/authz/model/ext/3.0" xmlns:old="http://thalesgroup.com/authzforce/pdp/model/2014/12" xmlns="http://authzforce.github.io/core/xmlns/pdp/5.0" xmlns:xacml="urn:oasis:names:tc:xacml:3.0:core:schema:wd-17" xmlns:pap-dao="http://authzforce.github.io/pap-dao-flat-file/xmlns/pdp-ext/3.6"
	exclude-result-prefixes="oldapi oldext old">
	<xsl:import href="../xacml3-policy-c14n.xsl" />
	<xsl:output encoding="UTF-8" indent="yes" method="xml" />
	<xsl:param name="basedir" select="." />
	<xsl:variable name="attributeFindersFileURI" select="concat($basedir, '/attributeFinders.xml')" />
	<xsl:variable name="rootPolicyFileURI" select="concat($basedir, '/policySet.xml')" />
	<xsl:variable name="refPoliciesFileURI" select="concat($basedir, '/refPolicySets.xml')" />

	<!-- Root policy -->
	<xsl:variable name="rootPolicy" select="document($rootPolicyFileURI)/xacml:PolicySet[1]" />

	<!-- PDP configuration -->
	<xsl:param name="maxVariableRefDepth" select="10" />
	<xsl:param name="maxPolicyRefDepth" select="10" />
	<!-- Single quotes to escape special character ':' -->
	<xsl:param name="requestFilter" select="'urn:ow2:authzforce:feature:pdp:request-filter:default-lax'" />
	<!-- WARNING 1: old policyFinder, resourceFinder, cache elements ignored/not supported. WARNING 2: if you use custom attribute finders, i.e. other than native CurrentDateTimeFinder or AttributeSelectorXPathFinder (in 'old' namespace), or if you use NON-standard datatypes / combining algorithms / functions, you have to add transformation rules to handle each of those. WARNING 3: old 'useStandard*' attributes are ignored (assume it is true always) -->
	<xsl:template match="old:pdps">
		<xsl:apply-templates select="document($refPoliciesFileURI)/oldapi:policySets/xacml:PolicySet" />
		<xsl:apply-templates select="$rootPolicy" />
		<pdp version="5.0.0" maxVariableRefDepth="{$maxVariableRefDepth}" maxPolicyRefDepth="{$maxPolicyRefDepth}" strictAttributeIssuerMatch="false" requestFilter="{$requestFilter}">
			<xsl:apply-templates select="old:attributeFactory/old:datatype" />
			<xsl:apply-templates select="old:functionFactory/old:target/old:function|old:functionFactory/old:condition/old:function|old:functionFactory/old:general/old:function" />
			<xsl:apply-templates select="old:functionFactory/old:target/old:abstractFunction|old:functionFactory/old:condition/old:abstractFunction|old:functionFactory/old:general/old:abstractFunction" />
			<xsl:apply-templates select="old:functionFactory/old:target/old:functionCluster|old:functionFactory/old:condition/old:functionCluster|old:functionFactory/old:general/old:functionCluster" />
			<xsl:apply-templates select="old:combiningAlgFactory/old:algorithm" />
			<xsl:apply-templates select="document($attributeFindersFileURI)/oldapi:attributeFinders" />
			<refPolicyProvider id="refPolicyProvider" xsi:type="pap-dao:StaticFlatFileDAORefPolicyProvider">
				<xsl:attribute name="policyLocationPattern">${PARENT_DIR}/policies/*.xml</xsl:attribute>
			</refPolicyProvider>
			<rootPolicyProvider id="rootPolicyProvider" xsi:type="StaticRefBasedRootPolicyProvider">
				<policyRef>
					<xsl:value-of select="$rootPolicy/@PolicySetId" />
				</policyRef>
			</rootPolicyProvider>
		</pdp>
	</xsl:template>

	<!-- Top-level PolicySets (from refPolicySets.xml and policySet.xml) -->
	<xsl:template match="/oldapi:policySets/xacml:PolicySet|/xacml:PolicySet">
		<!-- Encode PolicySetId with base64url -->
		<!-- Note: Non-free Saxon PE/EE has string-to-base64Binary() function (http://www.saxonica.com/html/documentation/functions/saxon/string-to-base64Binary.html). Unfortunately, we are using HE edition. -->
		<!-- Calling Java in XSLT is no longer supported by latest version of Saxon-HE. Or you have to use "integrated extension functions". http://stackoverflow.com/questions/19004719/calling-java-methods-in-xslt -->
		<!-- Calling string() makes sure Saxon matches method base64UrlEncode(String), otherwise Saxon throws an error because of ambiguous call (there are multiple base64UrlEncode() methods in the class) -->
		<xsl:variable name="encodedId" select="utils:base64UrlEncode(string(@PolicySetId))" xmlns:utils="java:org.ow2.authzforce.pap.dao.flatfile.FlatFileDAOUtils" />
		<xsl:variable name="outfileURI" select="concat($encodedId,'/', @Version, '.xml')" />
		<xsl:result-document href="policies/{$outfileURI}" omit-xml-declaration="no" method="xml">
			<xsl:call-template name="canonicalize-policy" />
		</xsl:result-document>
	</xsl:template>

	<!-- Attribute datatypes -->
	<xsl:template match="old:datatype">
		<attributeDatatype>
			<xsl:value-of select="@id" />
		</attributeDatatype>
	</xsl:template>

	<!-- Functions -->
	<xsl:template match="old:function">
		<function>
			<xsl:value-of select="@class" />
		</function>
	</xsl:template>
	<xsl:template match="old:abstractFunction">
		<function>
			<xsl:value-of select="@id" />
		</function>
	</xsl:template>

	<!-- Function sets -->
	<xsl:template match="old:functionCluster">
		<xsl:message terminate="yes">
			This upgrader tool does not support migration of 'functionCluster' elements.
			Please convert any 'functionCluster' to the equivalent sequence of 'function' elements (one per function in the cluster) in your PDP configuration files (pdp.xml) and try the upgrade tool again.
		</xsl:message>
	</xsl:template>

	<!-- Policy/Rule combining algorithms -->
	<xsl:template match="old:algorithm">
		<combiningAlgorithm>
			<xsl:value-of select="@class" />
		</combiningAlgorithm>
	</xsl:template>

	<!-- Attribute providers -->
	<xsl:template name="attribute-finders-upgrade" match="/oldapi:attributeFinders">
		<xsl:for-each select="oldext:attributeFinder">
			<xsl:variable name="typeLocalName" select="substring-after(@xsi:type, ':')" />
			<attributeProvider id="ap{position()}">
				<!-- For each old type of "attributeFinder", define rules of transformation to the 'attributeProvider' in the new PDP model. This assumes the attribute provider implementation class and XML schema have been adapted to the new interface and XML type. -->
				<xsl:choose>
					<!-- Example of XML conversion of some attribute finder type 'TestAttributeFinder' to new 'TestAttributeProvider' type in new namespace 'http://authzforce.github.io/core/xmlns/test/3' -->
					<!-- In this example, the conversion is just a dumb copy of the XML content (child nodes), assuming this has not changed. -->
					<!-- Check if this is my attribute finder XML type, i.e. the local name (string after ':') in xsi:type is 'TestAttributeFinder' in the example -->
					<xsl:when test="$typeLocalName = 'TestAttributeFinder'">
						<xsl:namespace name="test" select="'http://authzforce.github.io/core/xmlns/test/3'" />
						<xsl:attribute name="xsi:type">test:TestAttributeProvider</xsl:attribute>
						<xsl:copy-of select="child::node()" />
					</xsl:when>
					<!-- Other <xsl:when> for other attribute finders ... -->
					<xsl:otherwise>
						<!-- Unexpected attributeFinder -->
						<xsl:message terminate="yes">
							Unsupported attributeFinder type in file 'attributeFinders.xml': ${typeLocalName}
						</xsl:message>
					</xsl:otherwise>
				</xsl:choose>
			</attributeProvider>
		</xsl:for-each>
	</xsl:template>
</xsl:stylesheet>
