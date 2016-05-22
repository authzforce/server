<?xml version="1.0" encoding="UTF-8"?>
<!-- Domain properties upgrade XSL Sheet: 4.2.0 -> 5.x. To be used with Saxon 
	XSLT processor. -->
<!-- Author: Cyril DANGERVILLE -->
<xsl:stylesheet version="2.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:old="http://authzforce.github.io/pap-dao-file/xmlns/properties/3.6"
	xmlns="http://authzforce.github.io/pap-dao-flat-file/xmlns/properties/3.6"
	exclude-result-prefixes="old">
	<xsl:output encoding="UTF-8" indent="yes" method="xml" />
	<xsl:param name="ignoreDomainName" />
	<xsl:variable name="setExternalId" select="not(boolean($ignoreDomainName))" />

	<xsl:template match="old:domainProperties">
		<domainProperties maxPolicyCount="10"
			maxVersionCountPerPolicy="10" versionRollingEnabled="true">
			<xsl:apply-templates select="@*,node()" />
		</domainProperties>
	</xsl:template>

	<xsl:template match="element()">
		<xsl:copy>
			<xsl:apply-templates select="@*,node()" />
		</xsl:copy>
	</xsl:template>

	<xsl:template match="attribute()|text()|comment()|processing-instruction()">
		<xsl:copy />
	</xsl:template>
</xsl:stylesheet>