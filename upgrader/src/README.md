{inceptionYear=${project.inceptionYear}}
{currentYear=${currentYear}}
# AuthZForce Upgrader

To upgrade AuhZForce data from a R4 version (4.2.x, 4.3.x or 4.4.x) to ${project.version}, proceed as follows:

1. Install Ivy and Ant-Contrib on your system:

    ```shell
    $ sudo apt-get install ivy ant-contrib
    ```
    
1. If you have custom AuthZForce PDP attribute providers, for each one, you have to adapt them to the new PDP AttributeProvider's Java interface, deploy and enable them on the new AuthZForce Server. Please refer to the [online User and Programmer Guide](http://readthedocs.org/projects/authzforce-ce-fiware/versions/) for more information on this process (select the latest version with the 3 first dot-separated numbers -- MAJOR.MINOR.PATCH -- matching your AuthZForce Server version). Then you have to add a new `xsl:when` element in the following form in the XSL template named `attribute-finders-upgrade` in XSL stylesheet `xslt/M.m.x/domain-pdp-upgrade.xsl` (path relative to the current working directory) -- replace `M.m` with the MAJOR.MINOR version of your old Authzforce version to be upgraded -- where you defined the transformation rules to upgrade the attribute provider configuration to the new model (the `TestAttributeProvider` below is just an example and may be ignored):

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
