{inceptionYear=${project.inceptionYear}}
{currentYear=${currentYear}}
# AuthZForce Upgrader

If you intend to install a new version of Authzforce on the same server as the old version, first create a backup of the folder `/opt/authzforce-ce-server`, and proceed with the instructions below, using the backup folder as `old.install.dir`.

To upgrade AuhZForce data from an older version to ${project.version}, proceed as follows:

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

1. Run the following command, where argument `old.version` is the old version (in the form `x.y.z`) of Authzforce you are upgrading from, argument `old.install.dir` is the installation directory of the old version, or a backup of it if you are installing the new version on the same server, and argument `new.install.dir` is the new installation directory of the Authzforce version corresponding to this upgrade tool:

    *WARNING 2: the following command will replace all standard XACML identifiers planned for deprecation in Appendix A.4 of XACML 3.0 Core specification with the new XACML 3.0 identifiers.*
    
    *WARNING 3: if you don't use `sudo`, make sure you are executing the command as a user with read-write permissions on `new.install.dir`.*
    
    ```shell
    $ sudo ant -Dold.version=6.0.0 \
      -Dold.install.dir=/path/to/old/opt/authzforce-ce-server-6.0.0 \
      -Dnew.install.dir=/path/to/new/opt/authzforce-ce-server \
    ```
    
1. Set the permissions properly on the new data:  
  
    ```shell
    $ sudo chown -RH tomcat8 /path/to/new/opt/authzforce-ce-server
    $ sudo chgrp -RH tomcat8 /path/to/new/opt/authzforce-ce-server
    ```

1. Restart Tomcat on the new AuthzForce server to load the new data.
