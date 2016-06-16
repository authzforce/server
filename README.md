# AuthZForce Server (Community Edition)
[![License badge](https://img.shields.io/badge/license-GPL-blue.svg)](https://opensource.org/licenses/GPL-3.0)
[![Documentation badge](https://readthedocs.org/projects/authzforce-ce-fiware/badge/?version=release-5.3.0)](http://authzforce-ce-fiware.readthedocs.io/en/release-5.3.0/?badge=release-5.3.0)
[![Docker badge](https://img.shields.io/docker/pulls/fiware/authzforce-ce-server.svg)](https://hub.docker.com/r/fiware/authzforce-ce-server/)
[![Support badge]( https://img.shields.io/badge/support-ask.fiware.org-yellowgreen.svg)](https://ask.fiware.org/questions/scope:all/sort:activity-desc/tags:authzforce/)

AuthZForce Server provides a multi-tenant RESTful API to Policy Administration Points (PAP) and Policy Decision Points (PDP) as defined in the [OASIS XACML 3.0 standard](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-core-spec-os-en.html).

AuthZForce Server is also the Reference Implementation (GEri) of [FIWARE](https://www.fiware.org) *Authorization PDP* Generic Enabler (GE). More info on the [FIWARE catalogue](http://catalogue.fiware.org/enablers/authorization-pdp-authzforce).
The manuals are available as downloadable HTML/PDF from the [releases page](https://github.com/authzforce/server/releases/latest), or online on [readthedocs.org](http://readthedocs.org/projects/authzforce-ce-fiware/versions/).

*If you are interested in using an embedded XACML-compliant PDP in your Java applications, AuthZForce also provides a PDP engine as a Java library in [Authzforce core project](http://github.com/authzforce/core).*

Note for contributers:
The sources for the manuals are located in [fiware repository](http://github.com/authzforce/fiware/doc).

## Features

### PDP (Policy Decision Point)
* Compliance with the following OASIS XACML 3.0 standards:
  * [XACML v3.0 Core standard](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-core-spec-os-en.html): all mandatory and optional features are supported, **except**: 
    * Elements `AttributesReferences`, `MultiRequests` and `RequestReference`;
    * Functions `urn:oasis:names:tc:xacml:3.0:function:xpath-node-equal`, `urn:oasis:names:tc:xacml:3.0:function:xpath-node-match` and `urn:oasis:names:tc:xacml:3.0:function:access-permitted`;
    * [Algorithms planned for future deprecation](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-core-spec-os-en.html#_Toc325047257).
  * [XACML v3.0 Core and Hierarchical Role Based Access Control (RBAC) Profile Version 1.0](http://docs.oasis-open.org/xacml/3.0/rbac/v1.0/xacml-3.0-rbac-v1.0.html)
  * [XACML v3.0 Multiple Decision Profile Version 1.0 - Repeated attribute categories](http://docs.oasis-open.org/xacml/3.0/multiple/v1.0/cs02/xacml-3.0-multiple-v1.0-cs02.html#_Toc388943334)  (`urn:oasis:names:tc:xacml:3.0:profile:multiple:repeated-attribute-categories`). 
  * Experimental support for:
    * [XACML Data Loss Prevention / Network Access Control (DLP/NAC) Profile Version 1.0](http://docs.oasis-open.org/xacml/xacml-3.0-dlp-nac/v1.0/xacml-3.0-dlp-nac-v1.0.html): only `dnsName-value` datatype and `dnsName-value-equal` function are supported;
    * [XACML 3.0 Additional Combining Algorithms Profile Version 1.0](http://docs.oasis-open.org/xacml/xacml-3.0-combalgs/v1.0/xacml-3.0-combalgs-v1.0.html): `on-permit-apply-second` policy combining algorithm;
    * [XACML v3.0 Multiple Decision Profile Version 1.0 - Requests for a combined decision](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-multiple-v1-spec-cd-03-en.html#_Toc260837890)  (`urn:oasis:names:tc:xacml:3.0:profile:multiple:combined-decision`). 
* Detection of circular XACML policy references (PolicySetIdReference); 
* Control of the **maximum XACML PolicySetIdReference depth**;
* Control of the **maximum XACML VariableReference depth**;
* Optional **strict multivalued attribute parsing**: if enabled, multivalued attributes must be formed by grouping all `AttributeValue` elements in the same Attribute element (instead of duplicate Attribute elements); this does not fully comply with [XACML 3.0 Core specification of Multivalued attributes (§7.3.3)](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-core-spec-os-en.html#_Toc325047176), but it usually performs better than the default mode since it simplifies the parsing of attribute values in the request;
* Optional **strict attribute Issuer matching**: if enabled, `AttributeDesignators` without Issuer only match request Attributes without Issuer (and same AttributeId, Category...); this option is not fully compliant with XACML 3.0, §5.29, in the case that the Issuer is indeed not present on a AttributeDesignator; but it is the recommended option when all AttributeDesignators have an Issuer (the XACML 3.0 specification (5.29) says: *If the Issuer is not present in the attribute designator, then the matching of the attribute to the named attribute SHALL be governed by AttributeId and DataType attributes alone.*);
* Extensibility points:
  * **Attribute Datatypes**: you may extend the PDP engine with custom XACML attribute datatypes;
  * **Functions**: you may extend the PDP engine with custom XACML functions;
  * **Combining Algorithms**: you may extend the PDP engine with custom XACML policy/rule combining algorithms;
  * **Attribute Providers**: you may plug custom attribute providers into the PDP engine to allow it to retrieve attributes from other attribute sources (e.g. remote service) than the input XACML Request during evaluation; 
  * **Request Filter**: you may customize the processing of XACML Requests before evaluation by the PDP core engine (e.g. used for implementing [XACML v3.0 Multiple Decision Profile Version 1.0 - Repeated attribute categories](http://docs.oasis-open.org/xacml/3.0/multiple/v1.0/cs02/xacml-3.0-multiple-v1.0-cs02.html#_Toc388943334));
  * **Result Filter**: you may customize the processing of XACML Results after evaluation by the PDP engine (e.g. used for implementing [XACML v3.0 Multiple Decision Profile Version 1.0 - Requests for a combined decision](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-multiple-v1-spec-cd-03-en.html#_Toc260837890));

### PAP (Policy Administration Point)
* Policy management: create/read/update/delete multiple policies and references from one to another (via PolicySetIdReference)
* Policy versioning: create/read/delete multiple versions per policy.
* Configurable root policy ID/version: top-level policy enforced by the PDP may be any managed policy (if no version defined in configuration, the latest available is selected)
* Configurable maximum number of policies;
* Configurable maximum number of versions per policy.
* Optional policy version rolling (when the maximum of versions per policy has been reached, oldest versions are automatically removed to make place).

### REST API
* Defined in standard [Web Application Description Language and XML schema](https://github.com/authzforce/rest-api-model/tree/develop/src/main/resources) so that you can automatically generate client code. 
* Provides access to all PAP/PDP features mentioned in previous sections.
* Multi-tenant: allows to have multiple domains/tenants, each with its own PAP/PDP, in particular its own policy repository.
* Conformance with [REST Profile of XACML v3.0 Version 1.0](http://docs.oasis-open.org/xacml/xacml-rest/v1.0/xacml-rest-v1.0.html) (at the level of each domain) except for test `urn:oasis:names:tc:xacml:3.0:profile:rest:assertion:home:pdp` (to be fixed in next release)
* [Fast Infoset](http://www.itu.int/en/ITU-T/asn1/Pages/Fast-Infoset.aspx) support for requests/responses.

### High availability and load-balancing
* Integration with file synchronization tools (e.g. [csync2](http://oss.linbit.com/csync2/)) or distributed filesystems (e.g. NFS and CIFS) to build clusters of AuthZForce Servers. 
