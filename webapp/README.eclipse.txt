(Install Eclipse Word Wrap Plug-in to avoid scrolling when reading/editing this file.)

Before deploying the webapp to Tomcat in Eclipse (using WTP tools), read and proceed as told below.

1) Copy the content of the directory at ../rest-service/src/test/resources/server.conf to a "authzforce" subdirectory in ${catalina.base}/conf of your Tomcat server runtime environment.

2) To customize the context.xml, copy the Environment/Parameter element you want to override from the project file 'Deployed/Resources/webapp/META-INF/context.xml.sample' into the context.xml of your Tomcat runtime environment folder in the 'Servers' project (automatically created with the Tomcat server runtime environment). 

3) Before publishing, if you have project 'authzforce-rest-api-model' opened in Eclipse, close it. If you do not, Eclipse will include ObjectFactory generated from oasis xacml model in authzforce-rest-api-model-XXX.jar deployed in the webapp; as a result, this class - a wrong side effect of maven-jaxb2-plugin - will override the one in oasis-xacml-model-XXX.jar, and cause com.sun.xml.bind.v2.runtime.IllegalAnnotationsException (because it does not declare all XACML shema elements but only the one required in authzforce-rest-api-model, therefore incomplete). Eclipse will not use the maven artifact which is packaged in a way that this ObjectFactory is excluded. Then publish the webapp to apply changes. 

4) If you wish to have all logs redirected to Eclipse console, replace "error" with "stdout" in the 'ref' attribute of each appender-ref with 'ref="error"' in the logback configuration file defined in step 3.

5) In the Enterprise version, you may enable the "secure" profile (API access control requiring SSL client certificate authentication, see later below) by opening the Tomcat Server editor, and click "Open launch configuration", Arguments tab. Add "-Dspring.profiles.active=secure" to VM arguments.

6) You may need to right-click on the server in Servers view and "Clean..."/"Clean Tomcat work directory" to apply changes.

