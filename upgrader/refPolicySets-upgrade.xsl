<?xml version="1.0" encoding="UTF-8"?>
<!-- Copyright (C) 2015 Thales Services SAS. The contents of this file are subject to the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. This file is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should have received 
	a copy of the GNU General Public License along with AuthZForce. If not, see <http://www.gnu.org/licenses/>. -->
<!-- Domain properties upgrade XSL Sheet: 4.2.0 -> 4.3.0 and above. To be used with Saxon XSLT processor. -->
<!-- Author: Cyril DANGERVILLE -->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:old="http://thalesgroup.com/authz/model/3.0" xmlns:xacml="urn:oasis:names:tc:xacml:3.0:core:schema:wd-17">
	<xsl:output encoding="UTF-8" indent="yes" method="xml" />
	<xsl:variable name="base64SpecChars" select="('+', '/')" />
	<xsl:variable name="base64UrlSpecChars" select="('-', '_')" />

	<xsl:template match="xacml:PolicySet">
		<!-- Encode PolicySetId with base64url -->
		<xsl:variable name="encodedId" select="replace-multi(string-to-base64Binary(@PolicySetId, 'UTF8'), base64SpecChars, base64UrlSpecChars)" />
		<xsl:variable name="outfileURI" select="concat(${encodedId},'/', @Version, '.xml')" />
		
		<xsl:result-document href="${outfileURI}" omit-xml-declaration="no" method="xml">
			<xsl:copy-of select="." />
		</xsl:result-document>
	</xsl:template>
</xsl:stylesheet>