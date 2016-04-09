<?xml version="1.0" encoding="UTF-8"?>
<!-- Domain properties upgrade XSL Sheet: 4.2.0 -> 4.3.0 and above. To be 
	used with Saxon XSLT processor. -->
<!-- Author: Cyril DANGERVILLE -->
<xsl:stylesheet version="2.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:old="http://thalesgroup.com/authz/model/3.0/resource"
	xmlns="http://authzforce.github.io/pap-dao-flat-file/xmlns/properties/3.6"
	exclude-result-prefixes="old">
	<xsl:output encoding="UTF-8" indent="yes" method="xml" />
	<xsl:template match="old:properties">
		<domainProperties externalId="{old:name}"
			maxPolicyCount="10" maxVersionCountPerPolicy="10"
			versionRollingEnabled="true">
			<description>
				<xsl:value-of select="old:description" />
			</description>
		</domainProperties>
	</xsl:template>
</xsl:stylesheet>