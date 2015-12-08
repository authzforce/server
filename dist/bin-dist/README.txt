== INSTALL FOR DEBIAN OR DEBIAN-BASED PLATFORMS ==
- Tester install sur ubuntu vierge
- pour ne pas créer de domaine par défaut:
	DEBIAN_FRONTEND=noninteractive
	More info: https://www.debian.org/releases/wheezy/s390x/ch05s02.html.fr
	
- Nouveau mode d'install (avec package .deb)
Sous ubuntu 14.04: apt-get devient juste apt (mais les "anciennes" commandes apt-get restent utilisables) 
$ sudo apt-get update
$ sudo apt-get install aptitude
$ sudo aptitude install gdebi curl
$ sudo gdebi authzforce_3.0.3-SNAPSHOT_all.deb


== GENERAL INSTALL FOR OTHER PLATFORMS ==
1) Default paths in the webapp configuration files are relative to the directory named after the system property "com.sun.aas.instanceRoot". This property is set automatically when deployed in Glassfish. However, when deployed to Tomcat, you have two options: 
a) "Quick and simple": set this system property with a JVM argument, e.g. in Tomcat catalina.sh script to the install directory of your choice. 
b) "Advanced" (more customizable): using Tomcat <Context> element to override various context parameters.

a) Option "Quick and simple": 

We will refer to it as variable ROOT_DIR.
Then create a directory named "config" in ROOT_DIR, then a directory named "authzforce" inside the new ROOT_DIR/config directory. Then create directory "logs" in ROOT_DIR as well. So now you have ROOT_DIR/config/authzforce and ROOT_DIR/logs directories.

- Before deploying the webapp to Tomcat, you must setup the configuration and logging directory. 2 options:
	- Quick and dirty: using a system property for the root directory containing configuration and logging directories
	- Advanced/more customizable: using Tomcat <Context> element.
	
1.a Quick and dirty option
Set the system property "com.sun.aas.instanceRoot", e.g. with JVM argument in catalina.sh script:
JAVA_OPTS=-Dcom.sun.aas.instanceRoot=/path/to/my/folder
Make sure

(Install Eclipse Word Wrap Plug-in to avoid scrolling when reading/editing this file.)

Before deploying the webapp to Tomcat in Eclipse (using WTP tools), read and proceed as told below.

2) Copy the content of the "conf" directory from the root of the Eclipse project to ROOT_DIR/config/authzforce.

3) Copy file "context.xml.sample" to "context.xml" in folder "src/main/webapp/META-INF" of your Eclipse project. This new "context.xml" file should never be committed to the Git repository. In fact, it should be part of the gitignore list to avoid being loaded when using Glassfish server.

4) Before publishing, if you have project 'authzforce-rest-api-model' - or any other project with JAXB classes generated from XSD importing XACML or other 3rd party schema - opened in Eclipse, close it. If you do not, Eclipse will include ObjectFactory generated from oasis xacml model in authzforce-rest-api-model-XXX.jar deployed in the webapp; as a result, this class - a wrong side effect of maven-jaxb2-plugin - will override the one in oasis-xacml-model-XXX.jar, and cause com.sun.xml.bind.v2.runtime.IllegalAnnotationsException (because it does not declare all XACML shema elements but only the one required in authzforce-rest-api-model, therefore incomplete). Eclipse will not use the maven artifact which is packaged in a way that this ObjectFactory is excluded. Then publish the webapp to apply changes. 

5) If you wish to have all logs redirected to Eclipse console, replace "error" with "stdout" in the 'ref' attribute of each appender-ref with 'ref="error"' in the logback configuration file defined in step 2.b .

6) For testing, if you do no want to use the "secure" profile (API access control requiring SSL client certificate authentication, see later below) which is the default profile, open Tomcat Server editor, click "Open launch configuration", Arguments tab. Add "-Dspring.profiles.active=unsecure" to VM arguments.

6) You may need to right-click on the server in Servers view and "Clean..."/"Clean Tomcat work directory" to apply changes.
