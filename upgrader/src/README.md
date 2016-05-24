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

1. Run the following command, where argument `old.version` is the old version (in the form `4.x.y`) of Authzforce you are upgrading from, argument `old.install.dir` is the installation directory of the old version, and argument `new.install.dir` is the new installation directory of the Authzforce version corresponding to this upgrade tool:

    *WARNING 1: by default, for each domain, the following command will convert the old domain property 'name' to the new 'externalId' property (the value is copied from one to the other during the upgrade).* **Make sure that each old domain 'name' is UNIQUE.** *Indeed, each 'externalId' MUST BE UNIQUE after the upgrade. If this is not the case, either fix it or skip this conversion step by adding the following argument: `-Dignore.domain.name=true`. In this case, the 'externalId' will not be set by the upgrader tool. This is not an issue for new AuthZForce versions since 'externalId' values are optional. You may set them later with the API if you need to.*

    *WARNING 2: the following command will replace all standard XACML identifiers planned for deprecation in Appendix A.4 of XACML 3.0 Core specification with the new XACML 3.0 identifiers.*
    
    *WARNING 3: if you don't use `sudo`, make sure you are executing the command as a user with read-write permissions on `new.install.dir`.*
    
    ```shell
    $ sudo ant -Dold.version=4.2.0 \
      -Dold.install.dir=/path/to/old/opt/authzforce-4.2.0 \
      -Dnew.install.dir=/path/to/new/opt/authzforce-ce-server \
    ```
    
    Another example with extra argument `ignore.domain.name` to skip domain name-to-externalId conversion, in case domain name properties of the old Authzforce installation are not unique:
    
    ```shell
    $ sudo ant -Dold.version=4.2.0 \
      -Dold.install.dir=/path/to/old/opt/authzforce-4.2.0 \
      -Dnew.install.dir=/path/to/new/opt/authzforce-ce-server \
      -Dignore.domain.name=true
    ```
    
1. Set the permissions properly on the new data:  
  
    ```shell
    $ sudo chown -RH tomcat7 /path/to/new/opt/authzforce-ce-server
    $ sudo chgrp -RH tomcat7 /path/to/new/opt/authzforce-ce-server
    ```

1. Restart Tomcat on the new AuthZForce server to load the new data.
