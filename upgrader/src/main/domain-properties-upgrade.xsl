<?xml version="1.0" encoding="UTF-8"?>
<!-- Domain properties upgrade XSL Sheet: 4.2.0 -> 5.x. To be used with Saxon XSLT processor. -->
<!-- Author: Cyril DANGERVILLE -->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:old="http://thalesgroup.com/authz/model/3.0/resource" xmlns:ns1="http://authzforce.github.io/pap-dao-flat-file/xmlns/properties/3.6"
   exclude-result-prefixes="old">
   <xsl:output encoding="UTF-8" indent="yes" method="xml" />
   <xsl:template match="old:properties">
<!--       <xsl:variable name="domain.name"> -->
<!--          <xsl:value-of select="old:name/text()" /> -->
<!--       </xsl:variable> -->
<!--       <xsl:message>Domain name: <xsl:value-of select="$domain.name" /></xsl:message> -->
      <ns1:domainProperties externalId="{old:name}" maxPolicyCount="10" maxVersionCountPerPolicy="10" versionRollingEnabled="true">
         <xsl:apply-templates/>
         <description>
            <xsl:value-of select="old:description|description" />
         </description>
      </ns1:domainProperties>
   </xsl:template>
   <xsl:template match="name|old:name">
   <xsl:message>Domain name: <xsl:value-of select="text()" /></xsl:message>
   </xsl:template>
</xsl:stylesheet>