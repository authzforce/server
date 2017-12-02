<?xml version="1.0" encoding="UTF-8"?>
<!-- Copyright (C) 2012-2016 Thales Services SAS. This file is part of AuthZForce CE. AuthZForce CE is free software: you can redistribute it and/or modify it under the terms of the GNU General Public 
   License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. AuthZForce CE is distributed in the hope that it will be useful, but WITHOUT 
   ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should have received a copy of the GNU General 
   Public License along with AuthZForce CE. If not, see <http://www.gnu.org/licenses/>. -->
<!-- PDP configuration upgrade XSL Sheet: 6.x,7.x -> 8.x. To be used with Saxon XSLT processor. Author: Cyril DANGERVILLE. -->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:old="http://authzforce.github.io/core/xmlns/pdp/5.0"
   xmlns="http://authzforce.github.io/core/xmlns/pdp/6.0" exclude-result-prefixes="old">
   <xsl:import href="../xacml3-policy-c14n.xsl" />
   <xsl:output encoding="UTF-8" indent="yes" method="xml" />

   <!-- Force xsi and pap-dao namespaces on root tag -->
   <xsl:template match="/*">
      <xsl:element name="{local-name(.)}">
         <xsl:namespace name="xsi" select="'http://www.w3.org/2001/XMLSchema-instance'" />
         <xsl:namespace name="pap-dao" select="'http://authzforce.github.io/pap-dao-flat-file/xmlns/pdp-ext/3.6'" />
         <xsl:attribute name="version">6.0.0</xsl:attribute>
         <xsl:apply-templates select="@*[name()!='version' and name()!='requestFilter' and name()!='resultFilter'] | node()" />
         <ioProcChain>
            <requestPreproc>
               <xsl:choose>
                  <xsl:when test="@requestFilter = 'urn:ow2:authzforce:feature:pdp:request-filter:multiple:repeated-attribute-categories-lax'">
                     <xsl:text>urn:ow2:authzforce:feature:pdp:request-preproc:xacml-xml:multiple:repeated-attribute-categories-lax</xsl:text>
                  </xsl:when>
                  <xsl:when test="@requestFilter = 'urn:ow2:authzforce:feature:pdp:request-filter:multiple:repeated-attribute-categories-strict'">
                     <xsl:text>urn:ow2:authzforce:feature:pdp:request-preproc:xacml-xml:multiple:repeated-attribute-categories-strict</xsl:text>
                  </xsl:when>
                  <xsl:when test="@requestFilter = 'urn:ow2:authzforce:feature:pdp:request-filter:default-lax'">
                     <xsl:text>urn:ow2:authzforce:feature:pdp:request-preproc:xacml-xml:default-lax</xsl:text>
                  </xsl:when>
                  <xsl:when test="@requestFilter = 'urn:ow2:authzforce:feature:pdp:request-filter:default-strict'">
                     <xsl:text>urn:ow2:authzforce:feature:pdp:request-preproc:xacml-xml:default-strict</xsl:text>
                  </xsl:when>
                  <xsl:when test="@requestFilter">
                     <xsl:value-of select="@requestFilter" />
                  </xsl:when>
                  <xsl:otherwise>
                     <xsl:text>urn:ow2:authzforce:feature:pdp:request-preproc:xacml-xml:default-lax</xsl:text>
                  </xsl:otherwise>
               </xsl:choose>
            </requestPreproc>
            <xsl:if test="@resultFilter">
               <resultPostproc>
                  <xsl:choose>
                     <xsl:when test="@resultFilter = 'urn:ow2:authzforce:feature:pdp:result-filter:multiple:test-combined-decision'">
                        <xsl:text>urn:ow2:authzforce:feature:pdp:result-postproc:xacml-xml:multiple:test-combined-decision</xsl:text>
                     </xsl:when>
                     <xsl:otherwise>
                        <xsl:value-of select="@resultFilter" />
                     </xsl:otherwise>
                  </xsl:choose>
               </resultPostproc>
            </xsl:if>
         </ioProcChain>
      </xsl:element>
   </xsl:template>

   <xsl:template match="@badRequestStatusDetailLevel">
      <xsl:attribute name="clientRequestErrorVerbosityLevel"><xsl:value-of select="." /></xsl:attribute>
   </xsl:template>

   <xsl:template match="old:functionSet">
      <xsl:message terminate="yes">
         This upgrader tool does not support migration of 'functionSet'
         (deprecated)
         elements because.
         Please convert any 'functionSet' to the
         equivalent sequence
         of 'function' elements (one per function
         in the
         set) in your PDP
         configuration files (pdp.xml) and try the upgrade
         tool again.
      </xsl:message>
   </xsl:template>

   <xsl:template match="element()">
      <xsl:element name="{local-name(.)}">
         <xsl:apply-templates select="@*,node()" />
      </xsl:element>
   </xsl:template>

   <xsl:template match="attribute()|text()|comment()|processing-instruction()">
      <xsl:copy />
   </xsl:template>
</xsl:stylesheet>
