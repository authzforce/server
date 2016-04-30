This document may be viewed in HTML form from this link: 
https://github.com/authzforce/server/tree/release-${project.version}/upgrader/src/main/README.md

# AuthZForce Upgrader

To upgrade AuhZForce data from version 4.2.0 to later, proceed as follows:
1. Install Ivy and Ant-Contrib on your system:

	```shell
	$ sudo apt-get install ivy ant-contrib
	```
	
1. Download AuthZForce server upgrader tool from the [Github project releases page](https://github.com/authzforce/server/releases/download/release-${project.version}/authzforce-ce-server-upgrader-${project.version}.tar.gz>). You get a file called ``authzforce-ce-server-upgrader-${project.version}.tar.gz``.
1. Copy this file to the host where the old AuthZForce Server is installed, and unzip it and change directory:

    ```shell
    $ tar xvzf authzforce-ce-server-upgrader-${project.version}.tar.gz
    $ cd authzforce-ce-server-upgrader-${project.version}
    ```
    
1. If you have custom AuthZForce PDP attribute providers, for each one, you have to adapt them to the new PDP AttributeProvider's Java interface, deploy and enable them on the new AuthZForce Server. Please refer to the [online Programmers Guide](http://authzforce-ce-fiware.readthedocs.io/en/latest/UserAndProgrammersGuide.html) for more information (select the version matching your software release at the bottom of the page). Then change the parent XML type in the XML schema to the AttributeProvider XML type from the new PDP extension model, then you have to add a new `xsl:when` element in the following form in the XSL template named *attribute-finders-upgrade* in XSL stylesheet `domain-pdp-upgrade.xsl` (in the current working directory), where you defined the transformation rules to upgrade the attribute finder configuration to the new model (the TestAttributeProvider below is just an example and may be ignored):

    ```xml
    <xsl:when test="$typeLocalName = 'TestAttributeFinder'">
	    <xsl:attribute name="xsi:type">test:TestAttributeProvider</xsl:attribute>
	    <!-- For this attribute finder (for example), we copy child nodes as is. -->
	    <xsl:copy-of select="child::node()" />
    </xsl:when>
    <xsl:when test="$typeLocalName = 'MyOldCustomAttributeFinder'">
	    <xsl:attribute name="xsi:type">my-new-namespace-prefix:MyCustomAttributeProvider</xsl:attribute>
	    <!-- Below the transformation rules for my custom attribute finder -->
	    ...
    </xsl:when>
    ```

3. Run the following command (only `new.data.dir` and `old.data.dir` properties are mandatory; `pdp.max.var.ref.depth` is required only if you want to set a specific maximum for VariableReference depth in XACML Policies, default is 10; `pdp.max.policy.ref.depth` is required only if you want to set a specific maximum for policy reference depth in XACML policies, default is 10; `pdp.request.filter` is required only if you want to enable support for the Multiple Decision Profile scheme based on repeated attribute categories, other schemes of this profile are not supported):

    *NB: `pdp.max.var.ref.depth`, `pdp.max.policy.ref.depth` and `pdp.request.filter` are optional. Remove them from the command to use default settings.*

    *WARNING 1: for each domain, the following command will replace the old domain property 'name' with the new 'externalId' property (the value is copied from one to the other during the upgrade).*

    *WARNING 2: the following command will replace all standard XACML identifiers planned for deprecation in Appendix A.4 of XACML 3.0 Core specification with the new XACML 3.0 identifiers.*
  
```shell
$ ant -Dold.data.dir=/path/to/old/opt/authzforce-4.2.0 \
      -Dnew.data.dir=/path/to/new/opt/authzforce-ce-server \
      -Dpdp.max.var.ref.depth=10 \
      -Dpdp.max.policy.ref.depth=10 \
      -Dpdp.request.filter=urn:ow2:authzforce:xacml:request-filter:multiple:repeated-attribute-categories-lax
```

This generates new data (e.g. domain directories) compatible with AuthZForce > v4.2.0 in location specified by `new.data.dir`.
