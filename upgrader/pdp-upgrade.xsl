<?xml version="1.0" encoding="UTF-8"?>
<!-- Copyright (C) 2015 Thales Services SAS. The contents of this file are 
	subject to the terms of the GNU General Public License as published by the 
	Free Software Foundation, either version 3 of the License, or (at your option) 
	any later version. This file is distributed in the hope that it will be useful, 
	but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
	or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
	more details. You should have received a copy of the GNU General Public License 
	along with AuthZForce. If not, see <http://www.gnu.org/licenses/>. -->
<!-- PDP configuration upgrade XSL Sheet: 4.2.0 -> 4.3.0 and above. To be 
	used with Saxon XSLT processor. -->
<!-- Author: Cyril DANGERVILLE -->
<xsl:stylesheet version="1.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xmlns:old="http://thalesgroup.com/authzforce/pdp/model/2014/12"
	xmlns="http://authzforce.github.io/core/xmlns/pdp/3.6"
	xmlns:pap-dao="http://authzforce.github.io/pap-dao-file/xmlns/pdp-ext/3.6">
	<xsl:output encoding="UTF-8" indent="yes" method="xml" />
	<!-- Parameters - default values -->
	<xsl:param name="maxVariableRefDepth">1</xsl:param>
	<xsl:param name="maxPolicyRefDepth">10</xsl:param>
	<xsl:param name="requestFilter">urn:oasis:names:tc:xacml:3.0:profile:multiple:repeated-attribute-categories-lax</xsl:param>
	<!-- WARNING 1: policyFinder, resourceFinder, cache elements not supported. 
		WARNING 2: if you use custom attribute finders, i.e. other than native CurrentDateTimeFinder 
		or AttributeSelectorXPathFinder (in 'old' namespace), or if you use NON-standard 
		datatypes / combining algorithms / functions, you have to add transformation 
		rules to handle each of those. Ignore useStandard* attributes (assume it 
		is true always) -->
	<xsl:template match="old:pdps">
		<pdp version="3.6.1">
			<xsl:attribute name="maxVariableRefDepth"><xsl:value-of select="$maxVariableRefDepth"/></xsl:attribute>
			<xsl:attribute name="maxPolicyRefDepth"><xsl:value-of select="$maxPolicyRefDepth"/></xsl:attribute>
			<xsl:attribute name="requestFilter"><xsl:value-of select="$requestFilter"/></xsl:attribute>
			<refPolicyProvider id="refPolicyProvider" xsi:type="pap-dao:StaticFileBasedDAORefPolicyProvider" policyLocationPattern="${PARENT_DIR}/policies/*.xml" />
			<rootPolicyProvider	id="rootPolicyProvider" xsi:type="StaticRefBasedRootPolicyProvider">
				<policyRef>root</policyRef>
			</rootPolicyProvider>
		</pdp>
	</xsl:template>
</xsl:stylesheet>