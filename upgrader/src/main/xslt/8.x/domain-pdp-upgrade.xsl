<?xml version="1.0" encoding="UTF-8"?>
<!-- Copyright (C) 2012-2016 Thales Services SAS. This file is part of AuthzForce CE. AuthzForce CE is free software: you can redistribute it and/or modify it under the terms of the GNU General Public 
	License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. AuthzForce CE is distributed in the hope that it will be useful, but WITHOUT 
	ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should have received a copy of the GNU General 
	Public License along with AuthzForce CE. If not, see <http://www.gnu.org/licenses/>. -->
<!-- PDP configuration upgrade XSL Sheet: parent folder name indicates the version from which you can upgrade to the current one. -->
<!-- To be used with Saxon XSLT processor. -->
<!-- Author: Cyril DANGERVILLE. -->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xmlns:old="http://authzforce.github.io/core/xmlns/pdp/6.0" xmlns="http://authzforce.github.io/core/xmlns/pdp/7" exclude-result-prefixes="old">
	<xsl:import href="../xacml3-policy-c14n.xsl" />
	<xsl:output encoding="UTF-8" indent="yes" method="xml" />

	<xsl:template match="old:refPolicyProvider">
		<policyProvider id="rootPolicyProvider" xsi:type="pap-dao:StaticFlatFileDaoPolicyProviderDescriptor">
			<xsl:apply-templates select="@policyLocationPattern" />
		</policyProvider>
	</xsl:template>

	<xsl:template match="old:rootPolicyProvider">
		<rootPolicyRef>
			<xsl:value-of select="old:policyRef" />
		</rootPolicyRef>
	</xsl:template>

	<!-- Force xsi and pap-dao namespaces on root tag -->
	<xsl:template match="/*">
		<xsl:element name="{local-name(.)}">
			<xsl:namespace name="xsi" select="'http://www.w3.org/2001/XMLSchema-instance'" />
			<xsl:namespace name="pap-dao" select="'http://authzforce.github.io/pap-dao-flat-file/xmlns/pdp-ext/4'" />
			<xsl:attribute name="version">7.1</xsl:attribute>
			<xsl:apply-templates select="@*[name()!='version'] | node()" />
		</xsl:element>
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
