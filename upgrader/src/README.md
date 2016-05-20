This document may be viewed in HTML form from this link: 
https://github.com/authzforce/server/blob/release-${project.version}/upgrader/src/README.md

# AuthZForce Upgrader

To upgrade AuhZForce data from version 4.2.0 to later, proceed as follows:

1. Install Ivy and Ant-Contrib on your system:

    ```shell
    $ sudo apt-get install ivy ant-contrib
    ```
    
1. Download AuthZForce server upgrader tool from [Maven Central Repository](http://repo1.maven.org/maven2/org/ow2/authzforce/authzforce-ce-server-upgrader/${project.version}/authzforce-ce-server-upgrader-${project.version}.tar.gz). You get a file called ``authzforce-ce-server-upgrader-${project.version}.tar.gz``.
1. Copy this file to the host where the old AuthZForce Server is installed, and unzip it and change directory:

    ```shell
    $ tar xvzf authzforce-ce-server-upgrader-${project.version}.tar.gz
    $ cd authzforce-ce-server-upgrader-${project.version}
    ```
    
1. If you have custom AuthZForce PDP attribute providers, for each one, you have to adapt them to the new PDP AttributeProvider's Java interface, deploy and enable them on the new AuthZForce Server. Please refer to the [online Programmers Guide](http://authzforce-ce-fiware.readthedocs.io/en/latest/UserAndProgrammersGuide.html) for more information on this process (select the version matching your AuthZForce Server release at the bottom of the page). Then change the parent XML type in the XML schema to the AttributeProvider XML type from the new PDP extension model, then you have to add a new `xsl:when` element in the following form in the XSL template named *attribute-finders-upgrade* in XSL stylesheet `domain-pdp-upgrade.xsl` (in the current working directory), where you defined the transformation rules to upgrade the attribute finder configuration to the new model (the TestAttributeProvider below is just an example and may be ignored):

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
    
    *WARNING 3: if you don't use `sudo`, make sure you are executing the command as a user with read-write permissions on `new.install.dir`.
  
    ```shell
    $ sudo ant -Dold.install.dir=/path/to/old/opt/authzforce-4.2.0 \
      -Dnew.install.dir=/path/to/new/opt/authzforce-ce-server \
    ```
    
    Another example with extra options `pdp.max.var.ref.depth`, `pdp.max.policy.ref.depth` and `pdp.request.filter`:
    
    ```shell
    $ sudo ant -Dold.install.dir=/path/to/old/opt/authzforce-4.2.0 \
      -Dnew.install.dir=/path/to/new/opt/authzforce-ce-server \
      -Dpdp.max.var.ref.depth=10 \
      -Dpdp.max.policy.ref.depth=10 \
      -Dpdp.request.filter=urn:ow2:authzforce:xacml:request-filter:multiple:repeated-attribute-categories-lax
    ```
    
1. Set the permissions properly on the new data:  
  
    ```shell
    $ sudo chown -RH tomcat7 /path/to/new/opt/authzforce-ce-server
    $ sudo chgrp -RH tomcat7 /path/to/new/opt/authzforce-ce-server
    ```

1. This generates new data (e.g. domain directories) compatible with AuthZForce > v4.2.0 in location specified by `new.data.dir`. Restart Tomcat on the new AuthZForce server to load the new data.
