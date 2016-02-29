# Change log
All notable changes to this project are documented in this file following the [Keep a CHANGELOG](http://keepachangelog.com) conventions. 

## Unreleased
### Added
- Distribution upgrader: tool to upgrade from Authzforce 4.2.0 to later versions
- Support of Extended Indeterminate values in policy evaluation (XACML 3.0 Core specification, section 7.10-7.14, appendix C: combining algorithms)
- Option to enable automatic removal of oldest unused version(s) of any updated policy if max number of versions is exceeded (specified by a global property 'org.ow2.authzforce.domain.policy.maxVersionCount')
- Manual synchronization of domain cache with data directory via REST API, allows to force reloading domains' PDPs and externalIDs without restarting the webapp or server:
	- GET /domains forces re-synchronization of all domains
	- GET /domain/{domainId}/properties forces re-synchronization of externalId with domain properties file (properties.xml) in the domain directory
	- GET /domain/{domainId}/pap/properties forces re-synchronization of PDP with configuration file (pdp.xml) and policy files in subfolder 'policies' of the domain directory
	- GET /domain/{domainId}/pap/policies forces re-synchronization of PDP with configuration file (pdp.xml) and policy files in subfolder 'policies' of the domain directory
	- DELETE /domain/{domainId} forces removal of the domain from cache, and the domain directory if it still exists (removes from cache only if directory already removed)

### Changed
- Strategy for synchronizing cached domain's PDP and externalId-to-domain mapping with configuration files: no longer using Java WatchService, but each domain has a specific thread polling files in the domain directory's and checking their lastModifiedTime attribute for change:
	- If a given domain ID is requested and no matching domain in cache, but a matching domain directory is found, the domain is automatically synced to cache and the synchronizing thread created;
	- If the domain's directory found missing by the synchronizing thread, the thread deletes the domain from cache.
	- If any change to properties.xml (domain description, externalId) detected, externalId updated in cache
	- If any change to pdp.xml or the file of any policy used by the PDP, the PDP is reloaded.
- Support of REST API model v5.0.0: 
  - Root policy reference no longer set via path /domains/{domainId}/properties but via /domains/{domainId}/pap/properties
  - API allows the special keyword "latest" as version ID to get the latest version of a given policy (in addition to XACML version IDs like before), e.g. URL path /domains/{domainId}/pap/policies/P1/latest represents the latest version of policy "P1"
  - Path /domains/{domainId}/pap/properties gives the status of the PDP (date/time of last modification and active policies)

### Removed
- Dependency on commons-io, replaced with Java 7 java.nio.file API for recursive directory copy/deletion

### Fixed
- Debian/Ubuntu package dependencies: java7-jdk replaced with 'openjdk-7-jdk | oracle-java7-installer' to fix issues with APT installation of virtual packages (e.g. java7-jdk)


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
