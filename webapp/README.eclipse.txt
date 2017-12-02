(Install Eclipse Word Wrap Plug-in to avoid scrolling when reading/editing this file.)

Before deploying the webapp to Tomcat in Eclipse (using WTP tools), read and proceed as told below.

1) Copy the content of the directory 'src/test/resources/authzforce-ce-server' to some directory readable by your Tomcat server runtime environment. We'll refer the absolute path of this directory as AUTHZFORCE_BASE.

2) Copy the content of '../dist/src/webapp-context.xml' into the context.xml of your Tomcat runtime environment folder in the 'Servers' project (automatically created with the Tomcat server runtime environment)
and replace occurrences of '/opt/${productName}' with the AUTHZFORCE_BASE value in that file. 

3) Before publishing, if you have project 'authzforce-ce-rest-api-model' opened in Eclipse, close it. If you do not, Eclipse will include ObjectFactory generated from oasis xacml model in authzforce-ce-rest-api-model-XXX.jar deployed in the webapp; as a result, this class - a wrong side effect of maven-jaxb2-plugin - will override the one in oasis-xacml-model-XXX.jar, and cause com.sun.xml.bind.v2.runtime.IllegalAnnotationsException (because it does not declare all XACML shema elements but only the one required in authzforce-ce-rest-api-model, therefore incomplete). Eclipse will not use the maven artifact which is packaged in a way that this ObjectFactory is excluded. Then publish the webapp to apply changes. 

4) If you wish to have all logs redirected to Eclipse console, replace "error" with "stdout" in the 'ref' attribute of each appender-ref with 'ref="error"' in the logback configuration file defined in step 3.

5) You may enable the "fastinfoset" profile by opening the Tomcat Server editor, and click "Open launch configuration", Arguments tab. Add "-Dspring.profiles.active=+fastinfoset" to VM arguments.

6) You may need to right-click on the server in Servers view and "Clean..."/"Clean Tomcat work directory" to apply changes.

