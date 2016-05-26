<?xml version="1.0" encoding="UTF-8"?>
<!-- Domain properties upgrade XSL Sheet: 4.2.0 -> 5.x. To be used with Saxon 
	XSLT processor. -->
<!-- Author: Cyril DANGERVILLE -->
<xsl:stylesheet version="2.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:old="http://thalesgroup.com/authz/model/3.0/resource"
	xmlns="http://authzforce.github.io/pap-dao-flat-file/xmlns/properties/3.6"
	exclude-result-prefixes="old">
	<xsl:output encoding="UTF-8" indent="yes" method="xml" />
	<xsl:param name="ignoreDomainName" />
	<xsl:variable name="setExternalId" select="not(boolean($ignoreDomainName))" />
	
	<xsl:template match="old:properties">
<!-- 	<xsl:message>setExternalId: <xsl:value-of select="$setExternalId"/></xsl:message> -->
	<!-- Attribute value can be referred using AVT (Attribute Value Template): {XPath_expression}
	Example: <domainProperties externalId="{*:name}" ....>
	-->
		<domainProperties
			maxPolicyCount="10" maxVersionCountPerPolicy="10"
			versionRollingEnabled="true">
			<xsl:if test="$setExternalId">
				<xsl:attribute name="externalId" select="*:name" />
			</xsl:if>
			<xsl:if test="*:description">
				<description><xsl:value-of select="*:description"/></description>
			</xsl:if>
		</domainProperties>
	</xsl:template>
</xsl:stylesheet>