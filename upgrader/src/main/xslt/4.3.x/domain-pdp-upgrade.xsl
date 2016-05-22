<?xml version="1.0" encoding="UTF-8"?>
<!-- Copyright (C) 2012-2016 Thales Services SAS. This file is part of AuthZForce 
	CE. AuthZForce CE is free software: you can redistribute it and/or modify 
	it under the terms of the GNU General Public License as published by the 
	Free Software Foundation, either version 3 of the License, or (at your option) 
	any later version. AuthZForce CE is distributed in the hope that it will 
	be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of 
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General 
	Public License for more details. You should have received a copy of the GNU 
	General Public License along with AuthZForce CE. If not, see <http://www.gnu.org/licenses/>. -->
<!-- PDP configuration upgrade XSL Sheet: 4.3.0 -> 5.x and above. To be used 
	with Saxon XSLT processor. Author: Cyril DANGERVILLE. -->
<xsl:stylesheet version="2.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xmlns="http://authzforce.github.io/core/xmlns/pdp/3.6"
	xmlns:flat-file-dao="http://authzforce.github.io/pap-dao-flat-file/xmlns/pdp-ext/3.6">
	<xsl:import href="../xacml3-policy-c14n.xsl" />
	<xsl:output encoding="UTF-8" indent="yes" method="xml" />

	<!-- PDP configuration -->
	<xsl:param name="maxVariableRefDepth" select="10" />
	<xsl:param name="maxPolicyRefDepth" select="10" />
	<!-- Single quotes to escape special character ':' -->
	<xsl:param name="requestFilter"
		select="'urn:ow2:authzforce:xacml:request-filter:default-lax'" />

	<xsl:template match="@xsi:type[.=QName('http://authzforce.github.io/pap-dao-file/xmlns/pdp-ext/3.6','StaticFileBasedDAORefPolicyProvider')]">
		<xsl:attribute name="xsi:type">flat-file-dao:StaticFlatFileDAORefPolicyProvider</xsl:attribute>
	</xsl:template>
	
	<xsl:template match="*:rootPolicyProvider/policyRef/@Version">
	<!-- Do nothing, do not copy. -->
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
