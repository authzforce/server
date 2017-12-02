<?xml version="1.0" encoding="UTF-8"?>
<!-- Domain properties upgrade XSL Sheet: 4.2.0 -> 5.x. To be used with Saxon XSLT processor. -->
<!-- Author: Cyril DANGERVILLE -->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="http://authzforce.github.io/pap-dao-flat-file/xmlns/properties/3.6">
   <xsl:output encoding="UTF-8" indent="yes" method="xml" />

   <xsl:template match="@*|node()|comment()|processing-instruction()">
      <xsl:copy>
         <xsl:apply-templates select="@*|node()|comment()|processing-instruction()"/>
      </xsl:copy>
   </xsl:template>
</xsl:stylesheet>