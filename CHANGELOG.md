# Change log
All notable changes to this project are documented in this file following the [Keep a CHANGELOG](http://keepachangelog.com) conventions. 

## Unreleased
### Added
- REST API features (see *Changed* section for API changes):
	- URL path specific to PDP properties:
		- `GET /domains/{domainId}/pap/pdp.properties` gives properties of the PDP, including date/time of last modification and active/applicable policies (root policy and policies referenced directly/indirectly from root)
		- `PUT /domains/{domainId}/pap/pdp.properties` also allows to set PDP's root policy reference and enable PDP implementation-specific features, such as Multiple Decision Profile support (scheme 2.3 - repeated attribute categories)
	- URL path specific to PRP (Policy Repository Point) properties: `GET or PUT /domains/{domainId}/pap/prp.properties`: set/get properties `maxPolicyCount` (maximum number of policies), `maxVersionCount` (maximum number of versions per policy), `versionRollingEnabled` (enable policy version rolling, i.e. oldest versions auto-removed when the number of versions of a policy is about to exceed `maxVersionCount`) 
	- Special keyword `latest` usable as version ID pointing to the latest version of a given policy (in addition to XACML version IDs like before), e.g. URL path `/domains/{domainId}/pap/policies/P1/latest` points to the latest version of the policy `P1`
	- Fast Infoset support with new data representation type `application/fastinfoset` (in addition to `application/xml`) for all API payloads. Requires Authzforce Server to be started in a specific mode using JavaEE environment entry `spring.profiles.active`. Default type remains `application/xml` (default type is used when a wildcard is received as Accept header value from the client) 
	- API caches domains' PDPs and externalIds for performance reasons, but it is now possible to force re-synchronizing this domain cache after any change to the backend domain repository, i.e. reloading domains' PDPs and externalIDs without restarting the webapp or server:
		- `GET or HEAD /domains` forces re-synchronization of all domains
		- `GET or HEAD /domain/{domainId}/properties` forces re-synchronization of externalId with domain properties file (properties.xml) in the domain directory
		- `GET or HEAD /domain/{domainId}/pap/pdp.properties`; or `GET or HEAD /domain/{domainId}/pap/policies` forces re-synchronization of PDP with configuration file (`pdp.xml`) and policy files in subfolder `policies` of the domain directory
		- `DELETE /domain/{domainId}` forces removal of the domain from cache, and the domain directory if it still exists (removes from cache only if directory already removed)
	- Properties for controlling the size of incoming XML (`maxElementDepth`, `maxChildElements`, `maxAttributeCount`, `maxAttributeSize`, `maxTextLength`) corresponding to [CXF XML security properties](http://cxf.apache.org/docs/security.html#Security-XML) may be configured as [JavaEE Environment Entries](https://tomcat.apache.org/tomcat-7.0-doc/config/context.html#Environment_Entries) in Tomcat-specific Authzforce webapp context file (`authzforce-ce.xml`). Only `maxElementDepth` and `maxChildElements` are supported in Fast Infoset mode (due to issue [CXF-6848](https://issues.apache.org/jira/browse/CXF-6848)).
- Completed 100% compliance with XACML 3.0 Core Specification with support of Extended Indeterminate values in policy evaluation (XACML 3.0 Core specification, section 7.10-7.14, appendix C: combining algorithms)
- Distribution upgrader: tool to upgrade from Authzforce 4.2.0

### Changed
- Supported REST API model (authzforce-ce-rest-api-model) upgraded to **v5.1.1** with following changes: 
  - PDP's root policy reference set via method `PUT /domains/{domainId}/pap/pdp.properties` (instead of `PUT /domains/{domainId}/properties` in previous version)
  - URL path `/domains/{domainId}/pap/attribute.providers` replaces `/domains/{domainId}/pap/attributeProviders` from previous version, in order to apply better practices of REST API design (case-insensitive URLs) and to be consistent with new API paths `pdp.properties` and `prp.properties` (see *Added* section)
- Multiple Decision Profile disabled by default after domain creation (enabled by default in previous version)
- Backend flat-file database (DAO):
	- Format of `properties.xml` (domain properties): XML namespace changed from `http://authzforce.github.io/pap-dao-file/xmlns/properties/3.6` to `http://authzforce.github.io/pap-dao-flat-file/xmlns/properties/3.6`
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
- Github #1: deleted domain ID still returned by GET /domains?externalId=...
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
