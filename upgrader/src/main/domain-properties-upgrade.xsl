<?xml version="1.0" encoding="UTF-8"?>
<!--

    Copyright (C) 2012-2016 Thales Services SAS.

    This file is part of AuthZForce CE.

    AuthZForce CE is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    AuthZForce CE is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with AuthZForce CE.  If not, see <http://www.gnu.org/licenses/>.

-->
<!-- Copyright (C) 2015 Thales Services SAS. The contents of this file are subject to the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. This file is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should have received 
	a copy of the GNU General Public License along with AuthZForce. If not, see <http://www.gnu.org/licenses/>. -->
<!-- Domain properties upgrade XSL Sheet: 4.2.0 -> 4.3.0 and above. To be used with Saxon XSLT processor. -->
<!-- Author: Cyril DANGERVILLE -->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:old="http://thalesgroup.com/authz/model/3.0/resource" xmlns="http://authzforce.github.io/pap-dao-file/xmlns/properties/3.6" exclude-result-prefixes="old">
	<xsl:output encoding="UTF-8" indent="yes" method="xml" />
	<xsl:template match="old:properties">
		<domainProperties externalId="{old:name}">
			<description>
				<xsl:value-of select="old:description" />
			</description>
		</domainProperties>
	</xsl:template>
</xsl:stylesheet>