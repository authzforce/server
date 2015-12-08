(Install Eclipse Word Wrap Plug-in to avoid scrolling when reading/editing this file.)

Before deploying the webapp to Tomcat in Eclipse (using WTP tools), read and proceed as told below.

1) Default paths in the webapp configuration files are relative to the directory named after the system property "com.sun.aas.instanceRoot". This property is set automatically when deployed in Glassfish. However, when deployed to Tomcat, you need to set it to an install directory of your choice. We will refer to it as variable ROOT_DIR. To set the system property, open Tomcat Server editor, click "Open launch configuration", Arguments tab, then add "-Dcom.sun.aas.instanceRoot=<value of your ROOT_DIR>" to VM arguments, and OK. Then create a directory named "config" in ROOT_DIR, then a directory named "authzforce" inside the new ROOT_DIR/config directory. Then create directory "logs" in ROOT_DIR as well. So now you have ROOT_DIR/config/authzforce and ROOT_DIR/logs directories.

2) Copy the content of the "conf" directory from the root of the Eclipse project to ROOT_DIR/config/authzforce.

3) Copy file "context.xml.sample" to "context.xml" in folder "src/main/webapp/META-INF" of your Eclipse project. This new "context.xml" file should never be committed to the Git repository. In fact, it should be part of the gitignore list to avoid being loaded when using Glassfish server.

4) Before publishing, if you have project 'authzforce-rest-api-model' opened in Eclipse, close it. If you do not, Eclipse will include ObjectFactory generated from oasis xacml model in authzforce-rest-api-model-XXX.jar deployed in the webapp; as a result, this class - a wrong side effect of maven-jaxb2-plugin - will override the one in oasis-xacml-model-XXX.jar, and cause com.sun.xml.bind.v2.runtime.IllegalAnnotationsException (because it does not declare all XACML shema elements but only the one required in authzforce-rest-api-model, therefore incomplete). Eclipse will not use the maven artifact which is packaged in a way that this ObjectFactory is excluded. Then publish the webapp to apply changes. 

5) If you wish to have all logs redirected to Eclipse console, replace "error" with "stdout" in the 'ref' attribute of each appender-ref with 'ref="error"' in the logback configuration file defined in step 3.

6) For testing, if you do no want to use the "secure" profile (API access control requiring SSL client certificate authentication, see later below) which is the default profile, open Tomcat Server editor, click "Open launch configuration", Arguments tab. Add "-Dspring.profiles.active=unsecure" to VM arguments.

6) You may need to right-click on the server in Servers view and "Clean..."/"Clean Tomcat work directory" to apply changes.

