# AuthZForce Server (Community Edition)
[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Documentation badge](https://readthedocs.org/projects/authzforce-ce-fiware/badge/?version=latest)](http://authzforce-ce-fiware.readthedocs.io/en/latest/?badge=latest)
[![Docker badge](https://img.shields.io/docker/pulls/fiware/authzforce-ce-server.svg)](https://hub.docker.com/r/fiware/authzforce-ce-server/)
[![Support badge]( https://img.shields.io/badge/support-ask.fiware.org-yellowgreen.svg)](https://ask.fiware.org/questions/scope:all/sort:activity-desc/tags:authzforce/)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/cdb9dd59cbf04a95bfbfbdcf770bb7d8)](https://www.codacy.com/app/coder103/authzforce-ce-server?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=authzforce/server&amp;utm_campaign=Badge_Grade)

*This project is part of [FIWARE](https://www.fiware.org). More info on the [FIWARE catalogue](http://catalogue.fiware.org/enablers/authorization-pdp-authzforce).*

AuthZForce Server provides a multi-tenant RESTful API to Policy Administration Points (PAP) and Policy Decision Points (PDP) supporting Attribute-Based Access Control (ABAC), as defined in the [OASIS XACML 3.0 standard](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-core-spec-os-en.html).

AuthZForce Server is also the Reference Implementation (GEri) of [FIWARE](https://www.fiware.org) *Authorization PDP* Generic Enabler (GE). More info on the [FIWARE catalogue](http://catalogue.fiware.org/enablers/authorization-pdp-authzforce).

**Go to the [releases](https://github.com/authzforce/server/releases) page for specific release info: downloads (Linux packages), Docker image, [release notes](CHANGELOG.md), and [documentation](http://readthedocs.org/projects/authzforce-ce-fiware/versions/).**

*If you are interested in using an embedded XACML-compliant PDP in your Java applications, AuthZForce also provides a PDP engine as a Java library in [Authzforce core project](http://github.com/authzforce/core).*


## Features

### PDP (Policy Decision Point)
* Compliance with the following OASIS XACML 3.0 standards:
  * [XACML v3.0 Core standard](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-core-spec-os-en.html)
  * [XACML v3.0 Core and Hierarchical Role Based Access Control (RBAC) Profile Version 1.0](http://docs.oasis-open.org/xacml/3.0/rbac/v1.0/xacml-3.0-rbac-v1.0.html)
  * [XACML v3.0 Multiple Decision Profile Version 1.0 - Repeated attribute categories](http://docs.oasis-open.org/xacml/3.0/multiple/v1.0/cs02/xacml-3.0-multiple-v1.0-cs02.html#_Toc388943334)  (`urn:oasis:names:tc:xacml:3.0:profile:multiple:repeated-attribute-categories`). 
  * [XACML v3.0 - JSON Profile Version 1.0](http://docs.oasis-open.org/xacml/xacml-json-http/v1.0/xacml-json-http-v1.0.html), with extra security features:
      * JSON schema [Draft v6](https://tools.ietf.org/html/draft-wright-json-schema-01) validation;
      * DoS mitigation: JSON parser variant checking max JSON string size, max number of JSON keys/array items and max JSON object depth.
  * Experimental support for:
      * [XACML Data Loss Prevention / Network Access Control (DLP/NAC) Profile Version 1.0](http://docs.oasis-open.org/xacml/xacml-3.0-dlp-nac/v1.0/xacml-3.0-dlp-nac-v1.0.html): only `dnsName-value` datatype and `dnsName-value-equal` function are supported;
      * [XACML 3.0 Additional Combining Algorithms Profile Version 1.0](http://docs.oasis-open.org/xacml/xacml-3.0-combalgs/v1.0/xacml-3.0-combalgs-v1.0.html): `on-permit-apply-second` policy combining algorithm;
      * [XACML v3.0 Multiple Decision Profile Version 1.0 - Requests for a combined decision](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-multiple-v1-spec-cd-03-en.html#_Toc260837890)  (`urn:oasis:names:tc:xacml:3.0:profile:multiple:combined-decision`).
* Safety/Security:
  * Prevention of circular XACML policy references (PolicySetIdReference) as mandated by [XACML 3.0](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-core-spec-os-en.html#_Toc325047192);
  * Control of the **maximum XACML PolicySetIdReference depth**;
  * Prevention of circular XACML variable references (VariableReference) as mandated by [XACML 3.0](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-core-spec-os-en.html#_Toc325047185); 
  * Control of the **maximum XACML VariableReference depth**;
* Optional **strict multivalued attribute parsing**: if enabled, multivalued attributes must be formed by grouping all `AttributeValue` elements in the same Attribute element (instead of duplicate Attribute elements); this does not fully comply with [XACML 3.0 Core specification of Multivalued attributes (§7.3.3)](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-core-spec-os-en.html#_Toc325047176), but it usually performs better than the default mode since it simplifies the parsing of attribute values in the request;
* Optional **strict attribute Issuer matching**: if enabled, `AttributeDesignators` without Issuer only match request Attributes without Issuer (and same AttributeId, Category...); this option is not fully compliant with XACML 3.0, §5.29, in the case that the Issuer is indeed not present on a AttributeDesignator; but it is the recommended option when all AttributeDesignators have an Issuer (the XACML 3.0 specification (5.29) says: *If the Issuer is not present in the attribute designator, then the matching of the attribute to the named attribute SHALL be governed by AttributeId and DataType attributes alone.*);
* Extensibility points:
  * **Attribute Datatypes**: you may extend the PDP engine with custom XACML attribute datatypes;
  * **Functions**: you may extend the PDP engine with custom XACML functions;
  * **Combining Algorithms**: you may extend the PDP engine with custom XACML policy/rule combining algorithms;
  * **Attribute Providers a.k.a. PIPs** (Policy Information Points): you may plug custom attribute providers into the PDP engine to allow it to retrieve attributes from other attribute sources (e.g. remote service) than the input XACML Request during evaluation; 
  * **Request Preprocessor**: you may customize the processing of XACML Requests before evaluation by the PDP core engine, e.g. used for supporting new XACML Request formats, and/or implementing [XACML v3.0 Multiple Decision Profile Version 1.0 - Repeated attribute categories](http://docs.oasis-open.org/xacml/3.0/multiple/v1.0/cs02/xacml-3.0-multiple-v1.0-cs02.html#_Toc388943334);
  * **Result Postprocessor**: you may customize the processing of XACML Results after evaluation by the PDP engine, e.g. used for supporting new XACML Response formats, and/or implementing [XACML v3.0 Multiple Decision Profile Version 1.0 - Requests for a combined decision](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-multiple-v1-spec-cd-03-en.html#_Toc260837890).

### PIP (Policy Information Point)
AuthzForce provides XACML PIP features in the form of *Attribute Providers*. More information in the previous section.

### PAP (Policy Administration Point)
* Policy management: create/read/update/delete multiple policies and references from one to another (via PolicySetIdReference)
* Policy versioning: create/read/delete multiple versions per policy.
* Configurable root policy ID/version: top-level policy enforced by the PDP may be any managed policy (if no version defined in configuration, the latest available is selected)
* Configurable maximum number of policies;
* Configurable maximum number of versions per policy.
* Optional policy version rolling (when the maximum of versions per policy has been reached, oldest versions are automatically removed to make place).

### REST API
* Provides access to all PAP/PDP features mentioned in previous sections with possibility to have PDP-only instances (i.e. without PAP features).
* Multi-tenant: allows to have multiple domains/tenants, each with its own PAP/PDP, in particular its own policy repository.
* Conformance with [REST Profile of XACML v3.0 Version 1.0](http://docs.oasis-open.org/xacml/xacml-rest/v1.0/xacml-rest-v1.0.html)
* Supported data formats, aka content types: 
	* `application/xml`: XML based on API schema;
	* `application/fastinfoset`: [Fast Infoset](http://www.itu.int/en/ITU-T/asn1/Pages/Fast-Infoset.aspx) based on API's XML schema;
	* `application/json`: JSON based on API's XML schema with a generic XML-to-JSON mapping convention
	* `application/xacml+xml`: XACML content only, as defined by [RFC 7061](https://tools.ietf.org/html/rfc7061)
	* `application/xacml+json`: JSON format for XACML Request/Response on PDP only, as defined by [XACML v3.0 - JSON Profile Version 1.0](http://docs.oasis-open.org/xacml/xacml-json-http/v1.0/xacml-json-http-v1.0.html)
* Defined in standard [Web Application Description Language and XML schema](https://github.com/authzforce/rest-api-model/tree/develop/src/main/resources) so that you can automatically generate client code.

### High availability and load-balancing
* Integration with file synchronization tools (e.g. [csync2](http://oss.linbit.com/csync2/)) or distributed filesystems (e.g. NFS and CIFS) to build clusters of AuthZForce Servers. 


## Limitations
The following optional features from [XACML v3.0 Core standard](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-core-spec-os-en.html) are not supported:
* Elements `AttributesReferences`, `MultiRequests` and `RequestReference`;
* Functions `urn:oasis:names:tc:xacml:3.0:function:xpath-node-equal`, `urn:oasis:names:tc:xacml:3.0:function:xpath-node-match` and `urn:oasis:names:tc:xacml:3.0:function:access-permitted`;
* [Algorithms planned for future deprecation](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-core-spec-os-en.html#_Toc325047257).

If you are interested in those, you can ask for [support](#Support).


## Distribution (downloads)
Every release is distributed as follows:
- Ubuntu/Debian package (recommended option): `.deb`;
- Other Linux distributions: `.tar.gz`;
- Docker image.

For download links, please go to the specific [release page](https://github.com/authzforce/server/releases).

## Documentation
For links to the documentation of a release, please go to the specific [release page](https://github.com/authzforce/server/releases).

## Examples of usage and PEP code with a web service authorization module
For an example of using an AuthzForce Server's RESTful PDP API in a real-life use case, please refer to the JUnit test class [RESTfulPdpBasedAuthzInterceptorTest](webapp/src/test/java/org/ow2/authzforce/webapp/test/pep/cxf/RESTfulPdpBasedAuthzInterceptorTest.java) and the Apache CXF authorization interceptor [RESTfulPdpBasedAuthzInterceptor](webapp/src/test/java/org/ow2/authzforce/webapp/test/pep/cxf/RESTfulPdpBasedAuthzInterceptor.java). The test class runs a test similar to @coheigea's [XACML 3.0 Authorization Interceptor test](https://github.com/coheigea/testcases/blob/master/apache/cxf/cxf-sts-xacml/src/test/java/org/apache/coheigea/cxf/sts/xacml/authorization/xacml3/XACML3AuthorizationTest.java) but using AuthzForce Server as PDP instead of OpenAZ. In this test, a web service client requests a Apache-CXF-based web service with a SAML token as credentials (previously issued by a Security Token Service upon successful client authentication) that contains the user ID and roles. Each request is intercepted on the web service side by a [RESTfulPdpBasedAuthzInterceptor](webapp/src/test/java/org/ow2/authzforce/webapp/test/pep/cxf/RESTfulPdpBasedAuthzInterceptor.java) that plays the role of PEP (Policy Enforcement Point in XACML jargon), i.e. it extracts the various authorization attributes (user ID and roles, web service name, operation...) and requests a decision with these attributes from a remote PDP provided by AuthzForce Server, then enforces the PDP's decision, i.e. forwards the request to the web service implementation if the decision is Permit, else rejects it.
For more information, see the Javadoc of [RESTfulPdpBasedAuthzInterceptorTest](webapp/src/test/java/org/ow2/authzforce/webapp/test/pep/cxf/RESTfulPdpBasedAuthzInterceptorTest.java).

## License
This project is licensed under the terms of GPL v3 except Java classes in packages `org.ow2.authzforce.webapp.org.apache.cxf.jaxrs.provider.json.utils` and `org.ow2.authzforce.webapp.org.codehaus.jettison.mapped` which are under Apache License.


## Support
Use the *Issues* tab on the Github repository page.
Please include as much information as possible; the more we know, the better the chance of a quicker resolution:

* Software version
* Platform (OS and JDK)
* Stack traces generally really help! If in doubt include the whole thing; often exceptions get wrapped in other exceptions and the exception right near the bottom explains the actual error, not the first few lines at the top. It's very easy for us to skim-read past unnecessary parts of a stack trace.
* Log output can be useful too; sometimes enabling DEBUG logging can help;
* Your code & configuration files are often useful.

If you wish to contact the developers for other reasons, use [Authzforce contact mailing list](http://scr.im/azteam).

## Contributing
### Documentation
The sources for the manuals are located in [fiware repository](http://github.com/authzforce/fiware/doc). 

### Releasing
1. From the develop branch, prepare a release (example using a HTTP proxy):
<pre><code>
    $ mvn -Dhttps.proxyHost=proxyhostname -Dhttps.proxyPort=8080 jgitflow:release-start
</code></pre>
1. Update the [changelog](CHANGELOG.md) with the new version according to keepachangelog.com.
1. Commit 
1. Perform the software release (example using a HTTP proxy):
<pre><code>
    $ mvn -Dhttps.proxyHost=proxyhostname -Dhttps.proxyPort=8080 jgitflow:release-finish
</code></pre>
    If, after deployment, the command does not succeed because of some issue with the branches. Fix the issue, then re-run the same command but with 'noDeploy' option set to true to avoid re-deployment:
<pre><code>
    $ mvn -Dhttps.proxyHost=proxyhostname -Dhttps.proxyPort=8080 -DnoDeploy=true jgitflow:release-finish
</code></pre>
  More info on jgitflow: http://jgitflow.bitbucket.org/
1. Connect and log in to the OSS Nexus Repository Manager: https://oss.sonatype.org/
1. Go to Staging Profiles and select the pending repository authzforce-*... you just uploaded with `jgitflow:release-finish`
1. Click the Release button to release to Maven Central.
1. When the artifacts have been successfully published on Maven Central, follow the instructions in the [Release section of fiware repository](https://github.com/authzforce/fiware/blob/master/README.md#release).
1. Build the Dockerfile by triggering Docker automated build on the current Github release branch in [authzforce-ce-server's Docker repository](https://hub.docker.com/r/authzforce/server/) (*Build Settings*). Check the result in *Build Details*.
1. Update the versions in badges at the top of this file.
1. Create a release on Github with a description based on the [release description template](release.description.tmpl.md), replacing M/m/P with the new major/minor/patch versions.
