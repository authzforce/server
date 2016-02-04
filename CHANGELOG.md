# Change log
All notable changes to this project are documented in this file following the [Keep a CHANGELOG](http://keepachangelog.com) conventions. 

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
- Global configuration properties: max number of policies per domain, max number of versions per domain
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
