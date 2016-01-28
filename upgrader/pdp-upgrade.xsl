<?xml version="1.0" encoding="UTF-8"?>
<!-- Copyright (C) 2015 Thales Services SAS. The contents of this file are subject to the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. This file is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should have received 
	a copy of the GNU General Public License along with AuthZForce. If not, see <http://www.gnu.org/licenses/>. -->
<!-- PDP configuration upgrade XSL Sheet: 4.2.0 -> 4.3.0 and above. To be used with Saxon XSLT processor. -->
<!-- Author: Cyril DANGERVILLE -->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:old="http://thalesgroup.com/authzforce/pdp/model/2014/12" xmlns="http://authzforce.github.io/core/xmlns/pdp/3.6" xmlns:xacml="urn:oasis:names:tc:xacml:3.0:core:schema:wd-17" xmlns:pap-dao="http://authzforce.github.io/pap-dao-file/xmlns/pdp-ext/3.6" xmlns:oldtest="http://example.com/test/attribute-finder" xmlns:newtest="http://example.com/newtest/attribute-finder">
	<xsl:output encoding="UTF-8" indent="yes" method="xml" />
	<xsl:variable name="base64SpecChars" select="('+', '/')" />
	<xsl:variable name="base64UrlSpecChars" select="('-', '_')" />

	<!-- Root policy -->
	<xsl:variable name="rootPolicy" select="document('policySet.xml')" />
	<xsl:template match="${rootPolicy}/xacml:PolicySet">
		<!-- Encode PolicySetId with base64url -->
		<xsl:variable name="encodedId" select="replace-multi(string-to-base64Binary(@PolicySetId, 'UTF8'), base64SpecChars, base64UrlSpecChars)" />
		<xsl:variable name="outfileURI" select="concat(${encodedId},'/', @Version, '.xml')" />
		<xsl:result-document href="policies/${outfileURI}" omit-xml-declaration="no" method="xml">
			<xsl:copy-of select="." />
		</xsl:result-document>
	</xsl:template>

	<!-- PDP configuration -->
	<xsl:param name="maxVariableRefDepth" select="1" />
	<xsl:param name="maxPolicyRefDepth" select="10" />
	<xsl:param name="requestFilter" select="urn:thalesgroup:xacml:request-filter:default-lax" />

	<!-- WARNING 1: policyFinder, resourceFinder, cache elements not supported. WARNING 2: if you use custom attribute finders, i.e. other than native CurrentDateTimeFinder or AttributeSelectorXPathFinder (in 'old' namespace), or if you use NON-standard datatypes / combining algorithms / functions, you have to add transformation rules to handle each of those. WARNING 3: old 'useStandard*' attributes are ignored (assume it is true always) -->
	<xsl:template match="old:pdps">
		<pdp version="3.6.1">
			<xsl:attribute name="maxVariableRefDepth"><xsl:value-of select="$maxVariableRefDepth" /></xsl:attribute>
			<xsl:attribute name="maxPolicyRefDepth"><xsl:value-of select="$maxPolicyRefDepth" /></xsl:attribute>
			<xsl:attribute name="requestFilter"><xsl:value-of select="$requestFilter" /></xsl:attribute>

			<!-- Attribute providers -->
			<xsl:template match="document('attributeFinders.xml')/attributeFinders/attributeFinder">
				<xsl:variable name="typeLocalName" select="substring-after(@xsi:type, ':')" />
				<xsl:variable name="position" select="position()" />
				<xsl:element name="attributeProvider">
					<xsl:attribute name="id">ap${position}</xsl:attribute>
					<xsl:choose>
						<xsl:when test="${typeLocalName} = 'TestAttributeFinder'">
							<xsl:attribute name="xsi:type">newTest:TestAttributeProvider</xsl:attribute>
							<xsl:element name="newtest:param1">
								<xsl:attribute name="attr1"><xsl:value-of select="test:param1/@attr1" /></xsl:attribute>
							</xsl:element>
							<newtest:param2><xsl:value-of select="test:param2" /></newtest:param2>
						</xsl:when>
						<xsl:otherwise>
							<!-- ERROR -->
							<xsl:message terminate="yes">Unsupported attributeProvider type in file 'attributeFinders.xml': ${typeLocalName}</xsl:message>
						</xsl:otherwise>
					</xsl:choose>
				</xsl:element>
			</xsl:template>
			<refPolicyProvider id="refPolicyProvider" xsi:type="pap-dao:StaticFileBasedDAORefPolicyProvider" policyLocationPattern="${PARENT_DIR}/policies/*.xml" />
			<rootPolicyProvider id="rootPolicyProvider" xsi:type="StaticRefBasedRootPolicyProvider">
				<policyRef>${rootPolicy}/@PolicySetId</policyRef>
			</rootPolicyProvider>
		</pdp>
	</xsl:template>
</xsl:stylesheet>