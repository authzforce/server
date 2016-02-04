# AuthZForce Upgrader

To upgrade AuhZForce data from version 4.2.0 to later, proceed as follows:
1. Install Ivy and Ant-Contrib on your system:
	```shell
	$ sudo apt-get install ivy ant-contrib
	```
2. If you have custom attribute finders, for each one, you have to adapt it to the new PDP AttributeProvider's Java interface, and change the parent XML type in the XML schema to the AttributeProvider XML type from the new PDP extension model, then you have to add a new `xsl:when` element in the following form in the XSL template named *attribute-finders-upgrade* in XSL stylesheet `pdp-upgrade.xsl`, where you defined the transformation rules to upgrade the attribute finder configuration to the new model (the TestAttributeProvider below is just an example and may be ignored):

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

3. Run in this directory (only `new.data.dir` and `old.data.dir` properties are mandatory; `pdp.max.var.ref.depth` is required only if you want to set a specific maximum for VariableReference depth in XACML Policies, default is 10; `pdp.max.policy.ref.depth` is required only if you want to set a specific maximum for policy reference depth in XACML policies, default is 10; `pdp.request.filter` is required only if you want to enable support for the Multiple Decision Profile scheme based on repeated attribute categories, other schemes of this profile are not supported):
*WARNING 1: for each domain, the following command will replace the property 'name' with the new 'externalId' property (the value is copied from one to the other during the upgrade).*
*WARNING 2: the following command will replace all standard XACML identifiers planned for deprecation in Appendix A.4 of XACML 3.0 Core specification with the new XACML 3.0 identifiers.*
  
```shell
$ ant -Dold.data.dir=/path/to/old/opt/authzforce-4.2.0 \
      -Dnew.data.dir=/path/to/new/opt/authzforce-ce-server \
      -Dpdp.max.var.ref.depth=10 \
      -Dpdp.max.policy.ref.depth=10 \
      -Dpdp.request.filter=urn:oasis:names:tc:xacml:3.0:profile:multiple:repeated-attribute-categories-lax
```

This generates new data (e.g. domain directories) compatible with AuthZForce > v4.2.0 in location specified by `new.data.dir`.
