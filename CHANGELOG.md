# Change log
All notable changes to this project are documented in this file following the [Keep a CHANGELOG](http://keepachangelog.com) conventions. We try to apply [FIWARE Versioning](https://forge.fiware.org/plugins/mediawiki/wiki/fiware/index.php/Releases_and_Sprints_numbering,_with_mapping_to_calendar_dates) with one particular rule: the version must be equal to or greater than the version of the _authzforce-ce-rest-api-model_ dependency (declared in _rest-service_ module's POM). Indeed, this dependency holds the resources of the REST API specification implemented by this project. Therefore, the rule helps relate a specific version of this project to the specific version of the REST API specification that is implemented/supported.

Issues reported on [GitHub](https://github.com/authzforce/server/issues) are referenced in the form of `[GH-N]`, where N is the issue number. Issues reported on [OW2](https://jira.ow2.org/browse/AUTHZFORCE/) are mentioned in the form of `[OW2-N]`, where N is the issue number.


## 12.0.1
### Fixed
- Fixed Docker image (Dockerfile) and Debian package: change of Tomcat version (9->10) and JRE version (11->17)
- Fixed class not found error when starting the webapp and missing CXF library for logging HTTP requests/responses: 
  - Added dependencies:
    - cxf-rt-features-logging: 4.0.3 
    - jakarta.xml.ws-api: 3.0.1


## 12.0.0
### Changed
- Upgraded parent project authzforce-ce-parent: 9.1.0
  - **Migrated to Java 17 (as the minimum required JRE version from now on), JAXB 4.0 (javax.xml.bind.\* imports replaced with jakarta.xml.bind.\*) and JAX-RS 3.0.0 (javax.ws.\* imports replaced with jakarta.ws.\*)**

- Upgraded dependencies: 
  - authzforce-ce-rest-api-model: 7.0.0 
    - authzforce-ce-atom-model: 9.1.0
	-  authzforce-ce-xacml-model: 9.1.0
	-  authzforce-ce-pdp-ext-model: 9.1.0 
    - Jakarta REST API: javax.ws.rs-api replaced with **jakarta.ws.rs-api: 3.0.0**
    - org.json:json: 20231013
  - authzforce-ce-jaxrs-utils: 3.0.0
    - authzforce-ce-xacml-json-model: 4.1.0
      - everit-json-schema: 1.14.3 
  - authzforce-ce-core-pap-dao-flat-file: 15.0.0
    - authzforce-ce-core-pap-api: 13.0.0
    - authzforce-ce-core-pdp-engine: 21.0.1:
	  - Fixed [GH-69](https://github.com/authzforce/core/issues/69): Support for XACML <StatusDetail> / <MissingAttributeDetail>s, returned when missing named Attribute(s) in AttributeDesignator/AttributeSelector expressions, and may be returned by custom PDP extensions as well.
	  - Fixed [GH-73](https://github.com/authzforce/core/issues/73): (Rule's Condition that always returns False was considered invalid).
	  - Fixed [GH-83](https://github.com/authzforce/core/issues/83): `NoSuchElementException` thrown when the rule combining algorithm is permit-unless-deny and there is no Deny rule but at least one Permit rule with Obligation/Advice.
	  - Fixed [GH-92](https://github.com/authzforce/core/issues/92) Deny-overrides rule combining algorithm - Missing obligations in case of multiple Permit Rules and no Deny Rule (only the Obligations from the first Permit Rule were returned).
    - authzforce-ce-core-pdp-io-xacml-json: 21.0.1
    - java-uuid-generator: 4.2.0
  - Servlet API: javax.servlet-api replaced with **jakarta.servlet-api: 5.0**.
  - jettison: 1.5.4
  - FastInfoset: 2.1.1
  - spring-context, spring-web, spring-core, spring-beans: 6.1.3
  - cxf-rt-*: 4.0.3
  - Logback: 1.4.14

- Upgraded Tomcat used in integration tests: 10.1.18

### Added
- Added support for GeoXACML content mime types on the REST API (WADL)

### Removed
- Removed dependency: logback-ext-spring


## 11.0.1
### Fixed
- Fixed Docker image: replaced vulnerable Tomcat base image with latest official (tomcat:9-jre11-temurin-focal) for Docker. [More info](https://github.com/authzforce/fiware/issues/22).


## 11.0.0
### Changed
- **Supported PDP configuration schema version is now 8.0 minimum: if you are already using AuthzForce Server 10.x or older and wish to migrate to this new version, follow the [Upgrader tool instructions](upgrader/src/README.md)**
- **PDP extensions' interfaces (Attribute Provider, Datatype, Function, Combining Algorithm) changed** to better support Multiple Decision Profile and namespace-aware XPath evaluation (e.g. AttributeSelector)
- PAP DAO API: New `AuthzPolicy` interface replacing (XACML-schema-derived JAXB-annotated) `PolicySet` in PAP DAO operations, as a more generic policy type.
- Upgraded parent project version to 8.2.1 with following dependencies:
  - Spring Core: 5.3.15
  - Apache CXF: 3.5.0
  - Logback: 1.2.10
- Upgraded dependencies:
  - authzforce-ce-pap-dao-flat-file to 14.0.0
  - authzforce-ce-core-pdp-* to 20.1.0
  - authzforce-ce-core-pap-api to 12.0.0
  - authzforce-ce-core-pdp-api to 21.2.0

### Added
- **New feature**: XPath variables in `AttributeSelector`s' and `xPathExpression` `AttributeValue`s' XPath expressions can now be defined by XACML `VariableDefinitions` (variable name used as XACML `VariableId`), which means XACML Variables can be used as XPath variables there.
- **New feature**: XACML `VariableReference`s can be used (indirectly) in `Match` elements through special `AttributeDesignators`, i.e. by enabling the new built-in Attribute Provider (`XacmlVariableBasedAttributeProvider` class) with an `attributeProvider` element of the new type `XacmlVarBasedAttributeProviderDescriptor` in PDP configuration, any `AttributeDesignator`s with `Category` matching the `attributeProvider/@category` in PDP configuration is handled as a `VariableReference` and the `AttributeId` is handled as the `VariableId`.

### Fixed
- [GH-66]: Support any XML namespace prefix declared on root PolicySet element in XACML AttributeSelectors' XPath expressions (namespace-aware evaluation).
- NullPointerException when log level is DEBUG
- CVEs


## 10.1.1
### Fixed
- CVE-2021-22696 and CVE-2021-3046 fixed by upgrading **authzforce-ce-parent to v8.0.3**
- GH-64: XACML/JSON schema loading fails when offline (no Internet connection), preventing AuthzForce Server to start (linked to everit-org/json-schema#88 )
    - Fixed by upgrading dependency authzforce-ce-xacml-json-model: 3.0.4
- Upgraded authzforce-ce-jaxrs-utils to 2.0.3 to match dependency authzforce-ce-xacml-json-model in same version
- Upgraded authzforce-ce-pap-dao-flat-file to 13.0.2 to match indirect dependency authzforce-ce-xacml-json-model in same version
  - Upgraded authzforce-ce-core-pdp-engine to 17.1.2


## 10.1.0
### Changed
- FastInfoset version: 2.0.0

### Fixed
- authzforce-ce-parent upgraded to v8.0.2 to fix CVE-2021-22118: 
  - Upgraded Spring: 5.2.15
- Dependency fixes:
  - authzforce-ce-pap-dao-flat-file: 13.0.1
  - authzforce-ce-core-pap-api: 11.0.1
  - authzforce-ce-core-pdp-engine: 17.1.1
    - authzforce-ce-core-pdp-api to 18.0.2
    - javax.mail to 1.6.2
  - authzforce-ce-jaxrs-utils: 2.0.2


## 10.0.0
### Changed
- Upgraded AuthzForce Parent: 8.0.0:
  - Upgraded to Java 11 support (Java 8 no longer supported)	
- Upgraded dependencies:
  - authzforce-ce-rest-api-model: 6.0.0
  -	authzforce-ce-jaxrs-utils: 2.0.1
  - authzforce-ce-core-pdp-engine: 17.1.0
  - authzforce-ce-core-pdp-io-xacml-json: 17.1.0
  -	authzforce-ce-core-pap-api: 11.0.0
  - authzforce-ce-pap-dao-flat-file: 13.0.0
  - authzforce-ce-core-pdp-api: 18.0.1	
  - authzforce-ce-xacml-json-model: 3.0.2
  - Jakarta RESTful Web Services: 2.1.6
  - JAXB (Jakarta XML Binding): 2.3.3
  - Apache CXF v3.4.1
  - Spring Boot Starter 2.3.5
  - Spring Core: 5.2.10 	
  - jettison: 1.4.1
  - org.json:json: v20190722 
  - org.everit.json.schema: 1.12.1	
  - SLF4J API: 1.7.30

### Added
- GH-61: JSON Object support in XACML/JSON Requests/Responses (as defined by JSON Profile of XACML), allowing custom XACML datatypes with JSON object structures.
- Support for validation of XACML/JSON requests (JSON Profile) with custom JSON schema stored in configuration directory, using new webapp environment property (e.g. specified in Tomcat webapp context) `org.ow2.authzforce.domains.xacmlJsonSchemaRelativePath` to be specified in `/etc/tomcat9/<Engine>/<Host>/authzforce-ce.xml`  (more info in [webapp-context.xml](dist/src/webapp-context.xml) )

### Fixed
- GH-62: duplicate declaration of namespace prefix now allowed
- CVE on jackson-databind -> v2.9.10.8
- CVE-2018-8088 affecting slf4j


## 9.0.1
### Fixed
- Tomcat startup error after Debian package install


## 9.0.0
*See the [Upgrader tool](upgrader/src) for upgrading from 8.x versions.*

### Added
- **Tomcat 9** support. 
- New application configuration variable `org.ow2.authzforce.webapp.badReqErrVerbosity`: configures the verbosity of HTTP 400 (Bad Request) responses to help clients troubleshoot their API requests. To be set in the webapp-specific Tomcat Context element, typically `/etc/tomcat9/Catalina/localhost/authzforce-ce.xml`.
- PDP API (/pdp): support for **Multiple Decision Profile with XACML/JSON Profile** (JSON input)

### Changed
- **Tomcat requirement: 9.x**. Although AuthzForce Server may still run on Tomcat 8 with a few tweaks, **Tomcat 8 is not officially supported anymore**.
- Domains' PDP configuration format changed, i.e. XML namespaces / types / elements changed  (the [Upgrader tool](upgrader/src) helps migrate configurations from older 8.x versions)
- Upgraded parent project (authzforce-ce-parent): 7.6.1: upgraded dependencies:
	- slf4j-api: 1.7.30 (fix CVE)
	- Apache CXF: 3.3.6
  	- Spring: 5.1.14
- Upgraded dependencies: 
	- authzforce-ce-core-pdp-engine: 16.0.0
	- authzforce-ce-core-pap-api: 10.1.0
	- authzforce-ce-jaxrs-utils: 1.6.0
	- authzforce-ce-pap-dao-flat-file: 12.0.0

### Fixed
- GH-46: bad PolicySets pushed to the /pap/policies endpoint are still saved on server side even if a HTTP 400 Bad Request is returned.
- Issues with XACML/JSON responses (XACML JSON Profile)
- CVE on slf4j


## 8.1.0
### Added
- [GH-29] Systematic input policy validation on API - HTTP POST `/domains/{domain-id}/pap/policies` - even if the policy is not currently in use by the PDP (it is potentially used later on after changing PDP configuration), in order to improve safety and troubleshooting. Policies are validated by attempting to load a temporary PDP configuration with the input policy as root policy.
- PDP extensions such as Attribute and Policy Providers can accept placeholders for system properties and environment variables in their string configuration parameters (as part of PDP configuration) and perform placeholder replacements with their factory method's input `EnvironmentProperties`. In particular, `policyLocation` elements in PDP's Policy Providers configuration now supports (not only `PARENT_DIR` property but also) system properties and environment variables (enclosed between `${...}`) with default value (separated from the property name by '!') if property/variable undefined. 

### Changed
- Parent project version: authzforce-ce-parent: 7.5.1
- Dependency versions:
	- authzforce-ce-pap-dao-flat-file: 11.0.0
	- authzforce-ce-core-pap-api: 10.0.0
	- authzforce-ce-core: 13.3.1
	- authzforce-ce-core-pdp-api: 15.3.0
	- authzforce-ce-jaxrs-utils: 1.3.1 
	- authzforce-ce-xacml-json-model: 2.1.1
	- json: 20171018
	- guava: 24.1.1-jre
	- slf4j-api: 1.7.2
	- Spring: 4.3.20
	- logback-classic: 1.2.3
	- logback-ext-spring: 0.1.5
	- Apache CXF: 3.2.55
	- Saxon-HE: 9.8.0-12
	- javax.mail-api (replaces mailapi): 1.6.0
	- jaxb2-basics: 1.11.1

### Fixed
- [GH-26] CVEs reported by OWASP dependency-check, esp. on Spring v4.3.14, and 4.3.18, upgraded to 4.3.20


## 8.0.1
### Fixed
- Typo in Debian package's Description field.


## 8.0.0
### Changed
- Parent project version: 5.1.0 -> 7.1.0:
- Dependency versions:
	- Guava: 21.0 -> 22.0
	- Spring: 4.3.6 -> 4.3.12
	- JAX-RS API (javax.ws.rs-api): 2.0.1 -> 2.1
	- CXF: 3.1.10 -> 3.2.1
	- AuthzForce Flat-file-based PAP DAO (authzforce-ce-pap-dao-flat-file): 8.1.0 -> 9.1.0:
		- AuthzForce Core (authzforce-ce-core): 7.1.0 -> 10.1.0
			- authzforce-ce-core-pdp-api: 9.1.0 -> 12.1.0
			- (new) authzforce-ce-xacml-json-model: 1.1.0, depends on:
				- org.everit.json.schema: 1.6.1
			- Domains' `pdp.xml` schema: 5.0.0 -> 6.0.0
				- `badRequestStatusDetailLevel` attribute replaced with `clientRequestErrorVerbosityLevel`
				- `requestFilter` (resp. `resultFilter`) attribute replaced with `requestPreproc` (resp. `resultPostproc`) element
		- AuthzForce Core PAP API (authzforce-ce-core-pap-api): 6.4.0 -> 9.1.0 
	- AuthzForce Server REST API Model (authzforce-ce-rest-api-model): 5.4.0 -> 5.7.0
- REST API resource `/pap/pdp.properties`: PDP feature identifiers named *...:request-filter:...* replaced with *...:request-preproc:...*, and *...:result-filter:...* with *...:result-postproc:...*.

### Added
- REST API improvements:
	- `[OW2-28]`: configurable base address in WADL via property `org.ow2.authzforce.webapp.publishedEndpointUrl` (JNDI environment entry), to make sure links in the WADL match the public endpoint address if the server is reached via a reverse-proxy
	- `[OW2-29]`: added XML-schema-based validation for JSON input (content-type `application/json`)
	- `[OW2-30]`: new `/version` resource on REST API, supporting GET method only and providing product metadata (name, version, release date, server uptime, REST API description URL) as a result.
	- `[OW2-32]`: improved error message when client attempt to POST a xacml Policy (instead of PolicySet) on `.../policies` resource
	- Support for [XACML JSON Profile](http://docs.oasis-open.org/xacml/xacml-json-http/v1.0/xacml-json-http-v1.0.html) with following security features:
		- JSON schema [Draft v6](https://tools.ietf.org/html/draft-wright-json-schema-01) validation, using new dependency: `org.everit.json.schema` (1.6.1);
		- DoS mitigation: JSON parser variant checking max JSON string size, max number of JSON keys/array items and max JSON object depth.
	- New supported content types: 
		- `application/xacml+json` for [XACML JSON Profile](http://docs.oasis-open.org/xacml/xacml-json-http/v1.0/xacml-json-http-v1.0.html) compliance, 
		- `application/xacml+xml` for [RFC 7061](https://tools.ietf.org/html/rfc7061) compliance.
	- Possibility to define different PDP input/output processing chains, i.e. `requestPreproc` (ex-requestFilter)/`resultPostproc` (ex-resultFilter) pairs, for different input/output (XACML request/response) formats, e.g. XACML/XML or XACML/JSON
	- New configuration properties (JNDI environment entries): 
		- `org.ow2.authzforce.domains.enableXacmlJsonProfile`: enable XACML JSON Profile support;
		- `org.ow2.authzforce.webapp.jsonKeysWithArrays`: JSON keys with values to be serialized always to arrays;
		- `org.ow2.authzforce.webapp.noNamespaceInJsonOutput`: drop namespaces in JSON output,
		- `org.ow2.authzforce.webapp.jsonKeysToXmlAttributes`: keys of JSON objects to be deserialized as XML attributes,
		- `org.ow2.authzforce.webapp.xmlAttributesToJsonLikeElements`: convert XML attributes like elements to JSON output (no `@` prefix).
	- New configuration file for configuring CXF/JAX-RS JSON Provider's `inTransformElements` [property](http://cxf.apache.org/docs/jax-rs-data-bindings.html#JAX-RSDataBindings-CustomizingJAXBXMLandJSONinputandoutput): `json-to-xml-map.properties`.
	- Uniqueness check on domains' `externalId` property (not two domains may have the same), before allowing to create new domain or changing a domain's externalId
- Faster webapp deployment (e.g. in Tomcat): added instructions to skip JAR scanning for annotations, TLDs, etc.
- Dependency for AuthzForce JAX-RS utility classes: `authzforce-ce-jaxrs-utils` (1.1.0)
- Dockerfile auto-generated with right software version by Maven build
- Domains' `pdp.xml` schema now allows administrators to define a `maxIntegerValue` attribute which specifies the max expected integer value to be handled by the PDP engine (during policy evaluation). Based on this value, AuthzForce uses a more optimal (in terms of CPU and memory usage) Java representation of integers (the smaller the better).


## 7.1.0
### Changed
- Project URL: https://tuleap.ow2.org/projects/authzforce -> https://authzforce.ow2.org
- GIT repository URL base: https://tuleap.ow2.org/plugins/git/authzforce -> https://gitlab.ow2.org/authzforce
- Versions of AuthzForce dependencies:
	- Parent project (authzforce-ce-parent): 5.1.0
	- authzforce-ce-pap-dao-flat-file: 8.1.0
	- authzforce-ce-core-pap-api: 6.4.0
	- authzforce-ce-core-pdp-api: 9.1.0 

- Dependency authzforce-ce-core replaced with authzforce-ce-core-pdp-engine with version 8.0.0 (authzforce-ce-core is now a multi-module project made of the core module `pdp-engine` and test utilities module `pdp-testutils` which is used by tests of webapp module)

### Added
- [Dockerfile](dist/src/docker/Dockerfile) for building Docker image of AuthzForce Server with minimal configuration 


## 7.0.0
### Changed
- Versions of AuthzForce dependencies:
	- Parent project (authzforce-ce-parent): 5.0.0
	- authzforce-ce-pap-dao-flat-file: 8.0.0
	- authzforce-ce-core-pap-api: 6.3.0
	- authzforce-ce-core: 7.1.0
	- authzforce-ce-core-pdp-api: 9.0.0 
		-> API changes (non-backward compatible) for PDP extensions:  DecisionCache, DecisionResultFilter
		
- Versions of third-party dependencies:
	- SLF4J: 1.7.22
	- Spring: 4.3.6
	- Guava: 21.0
	- CXF: 3.1.10
	- Logback-classic: 1.1.9

### Added
- Class [RESTfulPdpBasedAuthzInterceptor](webapp/src/test/java/org/ow2/authzforce/web/test/pep/cxf/RESTfulPdpBasedAuthzInterceptor): an example of PEP using PDP's REST API in the form of a CXF interceptor. More info on the test scenario in the associated test class [RESTfulPdpBasedAuthzInterceptorTest](webapp/src/test/java/org/ow2/authzforce/web/test/pep/cxf/RESTfulPdpBasedAuthzInterceptorTest).

### Fixed
- [OW2-25] NullPointerException when parsing Apply expressions using invalid/unsupported Function ID. This is the final fix addressing higher-order functions. Initial fix in v7.0.0 only addressed first-order ones.


## 6.0.0
### Added
- [GH-8] JSON support on the REST API using [*mapped* convention](http://cxf.apache.org/docs/json-support.html) with configurable namespace-to-JSON-prefix mappings (new configuration file `xmlns-to-json-key-prefix-map.properties`)
- [GH-9] Configuration parameter `enablePdpOnly` (boolean): disables all API features except the PDP if true. Allows to have PDP-only AuthzForce Server instances.
- PDP engine (AuthzForce Core) enhancements:
	- Extension mechanism to switch `HashMap`/`HashSet` implementations with different performance properties; default implementation is based on a mix of native JRE and Guava.
	- Static validation (at policy initialization time) of the 'n' argument (minimum of *true* arguments) of XACML 'n-of' function if this argument is constant (must be a positive integer not greater than the number of remaining arguments)
	- Static validation (at policy initialization time) of second and third arguments of XACML substring function if these are constants (arg1 >= 0 && (arg2 == -1 || arg2 >= arg1))

- Dependency vulnerability checking with OWASP dependency-check tool
- Source code security validation with Find Security Bugs plugin

### Changed
- Compatible Java version changed from 1.7 to **1.8**
- Packaging for **Ubuntu 16.04 LTS / JRE 8 / Tomcat 8**: changed Ubuntu package dependencies to `openjdk-8-jre | oracle-java8-installer, tomcat8`
- Upgraded parent project authzforce-ce-parent: 3.4.0 -> 4.1.1:
- Upgraded dependencies:
	- Guava dependency version: 18.0 -> 20.0
	- Saxon-HE dependency version: 9.6.0-5 -> 9.7.0-14
	- com.sun.mail:javax.mail v1.5.4 -> com.sun.mail:mailapi v1.5.6
	- Java Servlet API: 3.0.1 -> 3.1.0
	- Apache CXF: 3.1.0 -> 3.1.9
	- [GH-12] Spring framework: 3.2.2 -> 4.3.5
	- authzforce-ce-core: 5.0.2 -> 6.1.0
	- authzforce-ce-pap-dao-flat-file: 6.1.0 -> 7.0.0
	- authzforce-ce-core-pdp-api: 7.1.1 -> 8.2.0
- Behavior of *unordered* rule combining algorithms (deny-overrides, permit-overrides, deny-unless-permit and permit-unless deny), i.e. for which the order of evaluation may be different from the order of declaration: child elements are re-ordered for more efficiency (e.g. Deny rules evaluated first in case of deny-overrides algorithm), therefore the algorithm implementation, the order of evaluation in particular, now differs from ordered-* variants.

### Fixed
- [GH-6] Removing the latest version of a policy now possible using `latest` keyword: HTTP DELETE `/domains/{domainId}/policies/{policyId}/latest`
- [GH-11] Wrong response status code returned by API when trying to activate a policy with invalid/unsupported function ID (related to [OW2-25])
- Issues in dependency Authzforce Core:
	- [OW2-23] enforcement of XACML `RuleId`/`PolicyId`/`PolicySetId` uniqueness:
		- `PolicyId` (resp. `PolicySetId`) should be unique across all policies loaded by PDP so that `PolicyIdReferences` (resp. `PolicySetIdReferences`) in XACML Responses' `PolicyIdentifierList` element are absolute references to applicable policies (no ambiguity).
 		- [RuleId should be unique within a policy](https://lists.oasis-open.org/archives/xacml/201310/msg00025.html) -> A rule is globally uniquely identified by the parent PolicyId and the RuleId.
	- [OW2-25] NullPointerException when parsing Apply expressions using invalid/unsupported Function ID

### Removed
- Dependency on Koloboke, replaced by extension mechanism mentioned in *Added* section that would allow to switch from the default HashMap/HashSet implementation to Koloboke-based.


## 5.4.1
### Fixed
- [OW2-22] When handling the same XACML Request twice in the same JVM with the root PolicySet using deny-unless-permit algorithm over a Policy returning simple Deny (no status/obligation/advice) and a Policy returning Permit/Deny with obligations/advice, the obligation is duplicated in the final result at the second time this situation occurs.
- XACML `StatusCode` XML serialization/marshalling error when Missing Attribute info that is no valid anyURI is returned by PDP in a Indeterminate Result
- Other issues reported by Codacy

### Changed
- Parent project version: authzforce-ce-parent: 3.4.0
- Dependency versions: authzforce-ce-core-pap-api: 5.3.0, authzforce-ce-pap-dao-flat-file: 6.1.0
- Interpretation of XACML Request flag `ReturnPolicyId=true`, considering a policy as _applicable_ if and only if the decision is not `NotApplicable` and if it is not a root policy, the same goes for the enclosing policy. See also the [discussion on the xacml-comment mailing list](https://lists.oasis-open.org/archives/xacml-comment/201605/msg00004.html).
- AttributeProvider module API: new environmentProperties parameter in factories, allowing module configurations to use global Environment properties like `PARENT_DIR` variable
- New PDP XML configuration schema namespace (used in file `conf/domain.tmpl/pdp.xml`): `http://authzforce.github.io/core/xmlns/pdp/5.0` (previous namespace: `http://authzforce.github.io/core/xmlns/pdp/3.6`).
  - Removed `functionSet` element
  - Added `standardEnvAttributeSource` attribute (enum): sets the source for the Standard Current Time Environment Attribute values (current-date, current-time, current-dateTime): `PDP_ONLY`, `REQUEST_ELSE_PDP`, `REQUEST_ONLY`
  - Added `badRequestStatusDetailLevel` attribute (positive integer) sets the level of detail of the error message in `StatusDetail` returned in Indeterminate Results in case of bad Requests

### Added
- Upgrader tool now supporting migration from 5.1.x, 5.2.x, 5.3.x, 5.4.x to current (to help deal with PDP XML schema changes, esp. namespace)


## 5.4.0
### Added
- Conformance with [REST Profile of XACML v3.0 Version 1.0](http://docs.oasis-open.org/xacml/xacml-rest/v1.0/xacml-rest-v1.0.html), especially test assertion [urn:oasis:names:tc:xacml:3.0:profile:rest:assertion:home:pdp](http://docs.oasis-open.org/xacml/xacml-rest/v1.0/cs02/xacml-rest-v1.0-cs02.html#_Toc399235433) (FIWARE SEC-923).

### Changed
- REST API model (authzforce-ce-rest-api-model) version: 5.3.1: changed `elementFormDefault` to _qualified_ in the XML schema for API payloads (and only text and FastInfoset-encoded XML are supported, not JSON)
- [GH-5] Moved maven dependency `cxf-rt-frontend-jaxrs` from child module `rest-service` to child module `webapp`.


## 5.3.0
### Changed
- Version of dependency `authzforce-ce-pap-dao-flat-file` to `6.0.0`, causing changes to the REST API URL `/domains/{domainId}/pap/pdp.properties` regarding IDs of features of type `urn:ow2:authzforce:feature-type:pdp:request-filter`:
	- `urn:ow2:authzforce:xacml:request-filter:default-lax` changed to `urn:ow2:authzforce:feature:pdp:request-filter:default-lax`;
	- `urn:ow2:authzforce:xacml:request-filter:default-strict` changed to `urn:ow2:authzforce:feature:pdp:request-filter:default-strict`;
	- `urn:ow2:authzforce:xacml:request-filter:multiple:repeated-attribute-categories-strict` changed to `urn:ow2:authzforce:feature:pdp:request-filter:multiple:repeated-attribute-categories-strict`;
	- `urn:ow2:authzforce:xacml:request-filter:multiple:repeated-attribute-categories-lax` changed to `urn:ow2:authzforce:feature:pdp:request-filter:multiple:repeated-attribute-categories-lax`.


## 5.2.0
### Added
- REST API spec (authzforce-ce-rest-api-model) v5.1.0 support: enhanced management of PDP features, i.e. all supported features may be listed, and each feature may have a 'type' and an 'enabled' (true or false) state that can be updated via the API
- [GH-1] Supported configurable PDP features by type:
  - Type `urn:ow2:authzforce:feature-type:pdp:core` (PDP core engine features, as opposed to extensions below): `urn:ow2:authzforce:feature:pdp:core:xpath-eval` (experimental support for XACML AttributeSelector, xpathExpression datatype and xpath-node-count function), `urn:ow2:authzforce:feature:pdp:core:strict-attribute-issuer-match` (enable strict Attribute Issuer matching, i.e. AttributeDesignators without Issuer only match request Attributes with same AttributeId/Category but without Issuer)
  - [GH-1] Type `urn:ow2:authzforce:feature-type:pdp:data-type`: any custom XACML Data type extension
  - [GH-1] Type `urn:ow2:authzforce:feature-type:pdp:function`: any custom XACML function extension
  - Type `urn:ow2:authzforce:feature-type:pdp:function-set`: any set of custom XACML function extensions
  - [GH-2] Type `urn:ow2:authzforce:feature-type:pdp:combining-algorithm`: any custom XACML policy/rule combining algorithm extension
  - [GH-2] Type `urn:ow2:authzforce:feature-type:pdp:request-filter`: any custom XACML request filter + native ones, i.e. `urn:ow2:authzforce:xacml:request-filter:default-lax` (default XACML Core-compliant Individual Decision Request filter), `urn:ow2:authzforce:xacml:request-filter:default-strict` (like previous one except duplicate <Attribute> in a <Attributes> is not allowed), `urn:ow2:authzforce:xacml:request-filter:multiple:repeated-attribute-categories-lax` (request filter implenting XACML profile `urn:oasis:names:tc:xacml:3.0:profile:multiple:repeated-attribute-categories`), `urn:ow2:authzforce:xacml:request-filter:multiple:repeated-attribute-categories-strict` (like previous one except duplicate <Attribute> in a <Attributes> is not allowed)
  - [GH-2] Type `urn:ow2:authzforce:feature-type:pdp:result-filter`: any custom XACML Result filter extension
- [GH-4] Distribution upgrader now supporting all 4.x versions as old versions


## 5.1.2
### Added
- REST API features (see *Changed* section for API changes):
	- URL path specific to PDP properties:
		- `GET /domains/{domainId}/pap/pdp.properties` gives properties of the PDP, including date/time of last modification and active/applicable policies (root policy and policies referenced directly/indirectly from root)
		- `PUT /domains/{domainId}/pap/pdp.properties` also allows to set PDP's root policy reference and enable PDP implementation-specific features, such as Multiple Decision Profile support (scheme 2.3 - repeated attribute categories)
	- URL path specific to PRP (Policy Repository Point) properties: `GET or PUT /domains/{domainId}/pap/prp.properties`: set/get properties `maxPolicyCount` (maximum number of policies), `maxVersionCount` (maximum number of versions per policy), `versionRollingEnabled` (enable policy version rolling, i.e. oldest versions auto-removed when the number of versions of a policy is about to exceed `maxVersionCount`)
	- Special keyword `latest` usable as version ID pointing to the latest version of a given policy (in addition to XACML version IDs like before), e.g. URL path `/domains/{domainId}/pap/policies/P1/latest` points to the latest version of the policy `P1`
	- Fast Infoset support with new data representation type `application/fastinfoset` (in addition to `application/xml`) for all API payloads. Requires Authzforce Server to be started in a specific mode using [JavaEE Environment Entry](https://tomcat.apache.org/tomcat-7.0-doc/config/context.html#Environment_Entries) `spring.profiles.active` in Tomcat-specific Authzforce webapp context file (`authzforce-ce.xml`). Default type remains `application/xml` (default type is used when a wildcard is received as Accept header value from the client)
	- API caches domains' PDPs and externalIds for performance reasons, but it is now possible to force re-synchronizing this domain cache after any change to the backend domain repository, i.e. reloading domains' PDPs and externalIDs without restarting the webapp or server:
		- `GET or HEAD /domains` forces re-synchronization of all domains
		- `GET or HEAD /domains/{domainId}/properties` forces re-synchronization of externalId with domain properties file (properties.xml) in the domain directory
		- `GET or HEAD /domains/{domainId}/pap/pdp.properties`; or `GET or HEAD /domains/{domainId}/pap/policies` forces re-synchronization of PDP with configuration file (`pdp.xml`) and policy files in subfolder `policies` of the domain directory
		- `DELETE /domains/{domainId}` forces removal of the domain from cache, and the domain directory if it still exists (removes from cache only if directory already removed)
	- Properties for controlling the size of incoming XML (`maxElementDepth`, `maxChildElements`, `maxAttributeCount`, `maxAttributeSize`, `maxTextLength`) corresponding to [CXF XML security properties](http://cxf.apache.org/docs/security.html#Security-XML) may be configured as [JavaEE Environment Entries](https://tomcat.apache.org/tomcat-7.0-doc/config/context.html#Environment_Entries) in Tomcat-specific Authzforce webapp context file (`authzforce-ce.xml`). Only `maxElementDepth` and `maxChildElements` are supported in Fast Infoset mode (due to issue [CXF-6848](https://issues.apache.org/jira/browse/CXF-6848)).
- Completed 100% XACML 3.0 Core Specification compliance with support of Extended Indeterminate values in policy evaluation (XACML 3.0 Core specification, section 7.10-7.14, appendix C: combining algorithms)
- Distribution upgrader: tool to upgrade from Authzforce 4.2.0

### Changed
- Supported REST API model (authzforce-ce-rest-api-model) upgraded to **v5.1.1** with following changes:
  - PDP's root policy reference set via method `PUT /domains/{domainId}/pap/pdp.properties` (instead of `PUT /domains/{domainId}/properties` in previous version)
  - URL path `/domains/{domainId}/pap/attribute.providers` replaces `/domains/{domainId}/pap/attributeProviders` from previous version, in order to apply better practices of REST API design (case-insensitive URLs) and to be consistent with new API paths `pdp.properties` and `prp.properties` (see *Added* section)
- Multiple Decision Profile disabled by default after domain creation (enabled by default in previous version)
- Backend flat-file database (DAO):
	- Format of `properties.xml` (domain properties): XML namespace changed to `http://authzforce.github.io/pap-dao-flat-file/xmlns/properties/3.6` (instead of `http://authzforce.github.io/pap-dao-file/xmlns/properties/3.6` in previous version)
	- Format of `pdp.xml` (PDP): XML schema/namespace of PDP PolicyProvider configuration changed to `http://authzforce.github.io/pap-dao-flat-file/xmlns/pdp-ext/3.6` (instead of `http://authzforce.github.io/pap-dao-file/xmlns/pdp-ext/3.6` in previous version)
	- Strategy for synchronizing cached domain's PDP and externalId-to-domain mapping with configuration files: no longer using Java WatchService (not adapted to NFS or CIFS shares), but each domain has a specific thread polling files in the domain directory's and checking their `lastModifiedTime` attribute for change:
		- If a given domain ID is requested and no matching domain in cache, but a matching domain directory is found, the domain is automatically synced to cache and the synchronizing thread created;
		- If the domain's directory found missing by the synchronizing thread, the thread deletes the domain from cache.
		- If any change to `properties.xml` (domain description, externalId) detected, externalId updated in cache
		- If any change to `pdp.xml` or the file of any policy used by the PDP, the PDP is reloaded.
- ZIP distribution format (`.zip`) changed to tarball format (`.tar.gz`), more suitable for Unix/Linux environments.

### Removed
- Dependency on commons-io, replaced with Java 7 java.nio.file API for recursive directory copy/deletion

### Fixed
- [GH-6] deleted domain ID still returned by GET /domains?externalId=...
- FIWARE JIRA [SEC-870](https://jira.fiware.org/browse/SEC-870): Debian/Ubuntu package dependencies: `java7-jdk` replaced with `openjdk-7-jdk | oracle-java7-installer`
- Policy versions returned in wrong order by API


## 4.4.1
### Changed
- Default domain rootPolicyRef no longer has 'Version' specified so that the root policy is always the latest version added via the PAP (by default).

### Fixed
- Hiding file paths from error messages returned by the REST API  


## 4.4.0
### Added
- XACML 3.0: Support for new XACML 3.0 standard string functions: type-from-string and string-from-type where type can be any XACML datatype (boolean, integer, double, time, date, etc.), string-starts-with, string-ends-with, anyURI-ends-with, anyURI-starts-with, string-contains, anyURI-contains, string-substring, anyURI-substring.
- XACML 3.0: Support new xacml 3.0 standard higher-order bag functions: any-of, all-of, any-of-any, map.
- XACML 3.0: Suppport for new XACML 3.0 standard date/time functions: dateTime-add-dayTimeDuration, dateTime-add-yearMonthDuration, dateTime-subtract-dayTimeDuration, dateTime-subtract-yearMonthDuration, date-add-yearMonthDuration, date-subtract-yearMonthDuration, dayTimeDuration-one-and-only, dayTimeDuration-bag-size, dayTimeDuration-is-in, dayTimeDuration-bag, yearMonthDuration-one-and-only, yearMonthDuration-bag-size.
- REST API: Enable/Disable logging of API requests and responses with access info (timestamp, source IP address, requested URL path, requested method, message body...) for audit, debugging, troubleshooting purposes


## 4.3.0
### Added
- REST API: CRUD operations per policy with versioning at URL path /domains/{id}/pap/policies/{policyId}/{policyVersion}. Each {policyId}/{policyVersion} represents a specific XACML PolicySet Id/Version that can be referenced from the PDP's root PolicySet or from other policies via PolicySetIdReference
- REST API: Domain property 'externalId' to be set by the client when provisioning/updating a domain (like in SCIM REST API). May be used in query parameter to retrieve a domain resource.
- REST API: Domain property 'rootPolicyRef' to define the root policy via policy reference to one of the policies managed via URL path /domains/{id}/pap/policies/{policyId}/{policyVersion}.
- XACML 3.0: Suppport for new xacml 3.0 standard equality functions: string-equal-ignore-case, dayTimeDuration-equal, yearMonthDuration-equal.
- XACML 3.0: Support for VariableDefinitions/VariableReferences
- XACML 3.0: support of Indeterminate arguments in boolean functions (and, or, n-of), i.e. the function may evaluate successfully with Indeterminate arguments under certain conditions
  1. OR: If at least 1 True arg, then True regardless of Indeterminate args; else if at least 1 Indeterminate, return Indeterminate; else false.
  1. AND: If at least 1 False arg, then False regardless of Indeterminate args; else if at least 1 Indeterminate, then Indeterminate; else True.
  1. N-OF: similar to OR but checking whether at least N args are True instead of 1, in the remaining arguments; else there is/are n True(s) with n < N; if there are at least (N-n) Indeterminate, return Indeterminate; else return false.
- Global configuration properties: max number of policies per domain, max number of versions per policy
- Distribution as WAR

### Changed
- REST API: Base64url-encoded domain IDs, to make URL paths shorter.
- XML namespaces for REST API data model using public github.io URLs and schema versioning (namespace includes major version and usage of 'version' attribute in root schema element)

### Fixed
- Policy(Set) IDs rejected although valid per definition of xs:anyURI, e.g. if it contained space characters.
- Error if no subject, action or resource attributes in XACML request

### Security
- Detection of circular references in Policy(Set)IdReferences or VariableReference
- Configurable max allowed depth of PolicySetIdReference or VariableReference


## 4.2.0
### Added
- Distribution as Debian package
- XACML 3.0: Permit-unless-deny policy/rule combining algorithm
- XACML 3.0: Ordered-deny-overrides policy/rule combining algorithm
- XACML 3.0: Ordered-permit-overrides policy/rule combining algorithm
- XACML 3.0: Multiple Decision Profile, scheme 2.3 (repetition of attribute categories)


## 4.1.0
### Changed
- Initial release in open source
