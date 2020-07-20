# AuthzForce Server (Community Edition)

[![FIWARE Security](https://nexus.lab.fiware.org/static/badges/chapters/security.svg)](https://www.fiware.org/developers/catalogue/)
[![License: GPL v3](https://img.shields.io/github/license/authzforce/server.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Docker badge](https://img.shields.io/docker/pulls/authzforce/server.svg)](https://hub.docker.com/r/authzforce/server/)
[![](https://img.shields.io/badge/tag-authzforce-orange.svg?logo=stackoverflow)](http://stackoverflow.com/questions/tagged/authzforce)
[![Support badge](https://img.shields.io/badge/support-ask.fiware.org-yellowgreen.svg)](https://ask.fiware.org/questions/scope:all/sort:activity-desc/tags:authzforce/)
<br/>
[![Documentation badge](https://readthedocs.org/projects/authzforce-ce-fiware/badge/?version=latest)](http://authzforce-ce-fiware.readthedocs.io/en/latest/?badge=latest)
[![Build Status](https://travis-ci.org/authzforce/server.svg?branch=develop)](https://travis-ci.org/authzforce/server)
![Status](https://nexus.lab.fiware.org/static/badges/statuses/authzforce.svg)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/cdb9dd59cbf04a95bfbfbdcf770bb7d8)](https://www.codacy.com/app/coder103/authzforce-ce-server?utm_source=github.com&utm_medium=referral&utm_content=authzforce/server&utm_campaign=Badge_Grade)
[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Fauthzforce%2Fserver.svg?type=shield)](https://app.fossa.io/projects/git%2Bgithub.com%2Fauthzforce%2Fserver?ref=badge_shield)

AuthzForce Server provides a multi-tenant RESTful API to Policy Administration
Points (PAP) and Policy Decision Points (PDP) supporting Attribute-Based Access
Control (ABAC), as defined in the
[OASIS XACML 3.0 standard](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-core-spec-os-en.html).

This project is part of [FIWARE](https://www.fiware.org/). For more information
check the FIWARE Catalogue entry for
[Security](https://github.com/Fiware/catalogue/tree/master/security).

**Go to the [releases](https://github.com/authzforce/server/releases) page for
specific release info: downloads (Linux packages), Docker image,
[release notes](CHANGELOG.md), and
[documentation](http://readthedocs.org/projects/authzforce-ce-fiware/versions/).**

The roadmap of this FIWARE GE is described [here](ROADMAP.md).

_If you are interested in using an embedded XACML-compliant PDP in your Java
applications, AuthzForce also provides a PDP engine as a Java library in
[Authzforce core project](http://github.com/authzforce/core)._

|  :books: [Documentation](https://authzforce-ce-fiware.rtfd.io/) | :mortar_board: [Academy](https://fiware-academy.readthedocs.io/en/latest/security/authzforce) | :whale: [Docker Hub](https://hub.docker.com/r/authzforce/server/) |  :dart: [Roadmap](https://github.com/authzforce/server/blob/develop/ROADMAP.md)
|---|---|---|---|


## Contents

-   [Features](#features)
-   [Limitations](#limitations)
-   [Quality Assurance](#quality-assurance)
-   [Install](#install)
-   [Documentation](#documentation)
-   [Training Courses](#training-courses)
-   [Usage](#usage)
-   [Testing](#testing)
-   [Support](#support)
-   [Security](#security-vulnerability-reporting)
-   [Contributing](#contributing)
-   [License](#license)

## Features

### PDP (Policy Decision Point)

-   Compliance with the following OASIS XACML 3.0 standards:
    -   [XACML v3.0 Core standard](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-core-spec-os-en.html)
    -   [XACML v3.0 Core and Hierarchical Role Based Access Control (RBAC) Profile Version 1.0](http://docs.oasis-open.org/xacml/3.0/rbac/v1.0/xacml-3.0-rbac-v1.0.html)
    -   [XACML v3.0 Multiple Decision Profile Version 1.0 - Repeated attribute categories](http://docs.oasis-open.org/xacml/3.0/multiple/v1.0/cs02/xacml-3.0-multiple-v1.0-cs02.html#_Toc388943334)
        (`urn:oasis:names:tc:xacml:3.0:profile:multiple:repeated-attribute-categories`).
    -   [XACML v3.0 - JSON Profile Version 1.0](http://docs.oasis-open.org/xacml/xacml-json-http/v1.0/xacml-json-http-v1.0.html),
        with extra security features:
        -   JSON schema
            [Draft v6](https://tools.ietf.org/html/draft-wright-json-schema-01)
            validation;
        -   DoS mitigation: JSON parser variant checking max JSON string size,
            max number of JSON keys/array items and max JSON object depth.
    -   Experimental support for:
        -   [XACML Data Loss Prevention / Network Access Control (DLP/NAC) Profile Version 1.0](http://docs.oasis-open.org/xacml/xacml-3.0-dlp-nac/v1.0/xacml-3.0-dlp-nac-v1.0.html):
            only `dnsName-value` datatype and `dnsName-value-equal` function are
            supported;
        -   [XACML 3.0 Additional Combining Algorithms Profile Version 1.0](http://docs.oasis-open.org/xacml/xacml-3.0-combalgs/v1.0/xacml-3.0-combalgs-v1.0.html):
            `on-permit-apply-second` policy combining algorithm;
        -   [XACML v3.0 Multiple Decision Profile Version 1.0 - Requests for a combined decision](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-multiple-v1-spec-cd-03-en.html#_Toc260837890)
            (`urn:oasis:names:tc:xacml:3.0:profile:multiple:combined-decision`).
-   Safety/Security:
    -   Prevention of circular XACML policy references (PolicySetIdReference) as
        mandated by
        [XACML 3.0](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-core-spec-os-en.html#_Toc325047192);
    -   Control of the **maximum XACML PolicySetIdReference depth**;
    -   Prevention of circular XACML variable references (VariableReference) as
        mandated by
        [XACML 3.0](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-core-spec-os-en.html#_Toc325047185);
    -   Control of the **maximum XACML VariableReference depth**;
-   Optional **strict multivalued attribute parsing**: if enabled, multivalued
    attributes must be formed by grouping all `AttributeValue` elements in the
    same Attribute element (instead of duplicate Attribute elements); this does
    not fully comply with
    [XACML 3.0 Core specification of Multivalued attributes (§7.3.3)](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-core-spec-os-en.html#_Toc325047176),
    but it usually performs better than the default mode since it simplifies the
    parsing of attribute values in the request;
-   Optional **strict attribute Issuer matching**: if enabled,
    `AttributeDesignators` without Issuer only match request Attributes without
    Issuer (and same AttributeId, Category...); this option is not fully
    compliant with XACML 3.0, §5.29, in the case that the Issuer is indeed not
    present on a AttributeDesignator; but it is the recommended option when all
    AttributeDesignators have an Issuer (the XACML 3.0 specification (5.29)
    says: _If the Issuer is not present in the attribute designator, then the
    matching of the attribute to the named attribute SHALL be governed by
    AttributeId and DataType attributes alone._);
-   Extensibility points:
    -   **Attribute Datatypes**: you may extend the PDP engine with custom XACML
        attribute datatypes;
    -   **Functions**: you may extend the PDP engine with custom XACML
        functions;
    -   **Combining Algorithms**: you may extend the PDP engine with custom
        XACML policy/rule combining algorithms;
    -   **Attribute Providers a.k.a. PIPs** (Policy Information Points): you may
        plug custom attribute providers into the PDP engine to allow it to
        retrieve attributes from other attribute sources (e.g. remote service)
        than the input XACML Request during evaluation;
    -   **Request Preprocessor**: you may customize the processing of XACML
        Requests before evaluation by the PDP core engine, e.g. used for
        supporting new XACML Request formats, and/or implementing
        [XACML v3.0 Multiple Decision Profile Version 1.0 - Repeated attribute categories](http://docs.oasis-open.org/xacml/3.0/multiple/v1.0/cs02/xacml-3.0-multiple-v1.0-cs02.html#_Toc388943334);
    -   **Result Postprocessor**: you may customize the processing of XACML
        Results after evaluation by the PDP engine, e.g. used for supporting new
        XACML Response formats, and/or implementing
        [XACML v3.0 Multiple Decision Profile Version 1.0 - Requests for a combined decision](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-multiple-v1-spec-cd-03-en.html#_Toc260837890).

### PIP (Policy Information Point)

AuthzForce provides XACML PIP features in the form of _Attribute Providers_.
More information in the previous section.

### PAP (Policy Administration Point)

-   Policy management: create/read/update/delete multiple policies and
    references from one to another (via PolicySetIdReference)
-   Policy versioning: create/read/delete multiple versions per policy.
-   Configurable root policy ID/version: top-level policy enforced by the PDP
    may be any managed policy (if no version defined in configuration, the
    latest available is selected)
-   Configurable maximum number of policies;
-   Configurable maximum number of versions per policy.
-   Optional policy version rolling (when the maximum of versions per policy has
    been reached, oldest versions are automatically removed to make place).

### REST API

-   Provides access to all PAP/PDP features mentioned in previous sections with
    possibility to have PDP-only instances (i.e. without PAP features).
-   Multi-tenant: allows to have multiple domains/tenants, each with its own
    PAP/PDP, in particular its own policy repository.
-   Conformance with
    [REST Profile of XACML v3.0 Version 1.0](http://docs.oasis-open.org/xacml/xacml-rest/v1.0/xacml-rest-v1.0.html)
-   Supported data formats, aka content types:
    - `application/xml`: XML based on API schema; 
    - `application/fastinfoset`: [Fast Infoset](http://www.itu.int/en/ITU-T/asn1/Pages/Fast-Infoset.aspx) based on API's XML schema; 
    - `application/json`: JSON based on API's XMLschema with a generic XML-to-JSON mapping convention 
    - `application/xacml+xml`: XACML content only, as defined by [RFC 7061](https://tools.ietf.org/html/rfc7061) 
    - `application/xacml+json`: JSON format for XACML Request/Response on PDP only, as defined by [XACML v3.0 - JSON Profile Version 1.0](http://docs.oasis-open.org/xacml/xacml-json-http/v1.0/xacml-json-http-v1.0.html)
-   Defined in standard
    [Web Application Description Language and XML schema](https://github.com/authzforce/rest-api-model/tree/develop/src/main/resources)
    so that you can automatically generate client code.

### High availability and load-balancing

-   Integration with file synchronization tools (e.g.
    [csync2](http://oss.linbit.com/csync2/)) or distributed filesystems (e.g.
    NFS and CIFS) to build clusters of AuthzForce Servers.

## Limitations

The following optional features from
[XACML v3.0 Core standard](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-core-spec-os-en.html)
are not supported:

-   Elements `AttributesReferences`, `MultiRequests` and `RequestReference`;
-   Functions `urn:oasis:names:tc:xacml:3.0:function:xpath-node-equal`,
    `urn:oasis:names:tc:xacml:3.0:function:xpath-node-match` and
    `urn:oasis:names:tc:xacml:3.0:function:access-permitted`;
-   [Algorithms planned for future deprecation](http://docs.oasis-open.org/xacml/3.0/xacml-3.0-core-spec-os-en.html#_Toc325047257).

If you are interested in those, you can ask for [support](#support).

## Quality Assurance

This project is part of [FIWARE](https://fiware.org/) and has been rated as
follows:

-   **Version Tested:**
    ![ ](https://img.shields.io/badge/dynamic/json.svg?label=Version&url=https://fiware.github.io/catalogue/json/authzforce.json&query=$.version&colorB=blue)
-   **Documentation:**
    ![ ](https://img.shields.io/badge/dynamic/json.svg?label=Completeness&url=https://fiware.github.io/catalogue/json/authzforce.json&query=$.docCompleteness&colorB=blue)
    ![ ](https://img.shields.io/badge/dynamic/json.svg?label=Usability&url=https://fiware.github.io/catalogue/json/authzforce.json&query=$.docSoundness&colorB=blue)
-   **Responsiveness:**
    ![ ](https://img.shields.io/badge/dynamic/json.svg?label=Time%20to%20Respond&url=https://fiware.github.io/catalogue/json/authzforce.json&query=$.timeToCharge&colorB=blue)
    ![ ](https://img.shields.io/badge/dynamic/json.svg?label=Time%20to%20Fix&url=https://fiware.github.io/catalogue/json/authzforce.json&query=$.timeToFix&colorB=blue)
-   **FIWARE Testing:**
    ![ ](https://img.shields.io/badge/dynamic/json.svg?label=Tests%20Passed&url=https://fiware.github.io/catalogue/json/authzforce.json&query=$.failureRate&colorB=blue)
    ![ ](https://img.shields.io/badge/dynamic/json.svg?label=Scalability&url=https://fiware.github.io/catalogue/json/authzforce.json&query=$.scalability&colorB=blue)
    ![ ](https://img.shields.io/badge/dynamic/json.svg?label=Performance&url=https://fiware.github.io/catalogue/json/authzforce.json&query=$.performance&colorB=blue)
    ![ ](https://img.shields.io/badge/dynamic/json.svg?label=Stability&url=https://fiware.github.io/catalogue/json/authzforce.json&query=$.stability&colorB=blue)

## Install

Every release is packaged in various types of distribution and the installation depends on the distribution type:

-   Ubuntu/Debian package (recommended option): `.deb`. Use your usual Ubuntu/Debian APT to install the package;
-   Other Linux distributions: `.tar.gz` for any Linux distribution. More info in the [documentation](#documentation);
-   Docker image, installed/deployed with the usual docker container commands.

For download links, please go to the specific
[release page](https://github.com/authzforce/server/releases).

Once you downloaded the distribution of your preference, check the [documentation](#documentation) for more information.

## Documentation

For links to the documentation of a release, please go to the specific
[release page](https://github.com/authzforce/server/releases).

## Training Courses
### Academy Courses
- [AuthzForce](https://fiware-academy.readthedocs.io/en/latest/security/authzforce/)

### Tutorials

The following tutorials on **AuthzForce Server** are available:

- 405. [Identity Management - XACML Rule-based Permissions](https://fiware-tutorials.readthedocs.io/en/latest/xacml-access-rules/).
- 406. [Identity Management - Administrating XACML Rules](https://fiware-tutorials.readthedocs.io/en/latest/administrating-xacml/);

## Usage

This section gives examples of usage and PEP code with a web service authorization module.

For an example of using an AuthzForce Server's RESTful PDP API in a real-life
use case, please refer to the JUnit test class
[RESTfulPdpBasedAuthzInterceptorTest](webapp/src/test/java/org/ow2/authzforce/webapp/test/pep/cxf/RESTfulPdpBasedAuthzInterceptorTest.java)
and the Apache CXF authorization interceptor
[RESTfulPdpBasedAuthzInterceptor](webapp/src/test/java/org/ow2/authzforce/webapp/test/pep/cxf/RESTfulPdpBasedAuthzInterceptor.java).
The test class runs a test similar to @coheigea's
[XACML 3.0 Authorization Interceptor test](https://github.com/coheigea/testcases/blob/master/apache/cxf/cxf-sts-xacml/src/test/java/org/apache/coheigea/cxf/sts/xacml/authorization/xacml3/XACML3AuthorizationTest.java)
but using AuthzForce Server as PDP instead of OpenAZ. In this test, a web
service client requests a Apache-CXF-based web service with a SAML token as
credentials (previously issued by a Security Token Service upon successful
client authentication) that contains the user ID and roles. Each request is
intercepted on the web service side by a
[RESTfulPdpBasedAuthzInterceptor](webapp/src/test/java/org/ow2/authzforce/webapp/test/pep/cxf/RESTfulPdpBasedAuthzInterceptor.java)
that plays the role of PEP (Policy Enforcement Point in XACML jargon), i.e. it
extracts the various authorization attributes (user ID and roles, web service
name, operation...) and requests a decision with these attributes from a remote
PDP provided by AuthzForce Server, then enforces the PDP's decision, i.e.
forwards the request to the web service implementation if the decision is
Permit, else rejects it. For more information, see the Javadoc of
[RESTfulPdpBasedAuthzInterceptorTest](webapp/src/test/java/org/ow2/authzforce/webapp/test/pep/cxf/RESTfulPdpBasedAuthzInterceptorTest.java).

    
## Testing

To run unit tests, install Maven and type

```console
mvn test
```

## Support

You should use
[AuthzForce users' mailing list](https://mail.ow2.org/wws/info/authzforce-users)
as first contact for any communication about AuthzForce: question, feature
request, notification, potential issue (unconfirmed), etc.

If you are experiencing any bug with this project and you indeed confirm this is
not an issue with your environment (contact the users mailing list first if you
are unsure), please report it on the
[OW2 Issue Tracker](https://gitlab.ow2.org/authzforce/server/issues). Please include as
much information as possible; the more we know, the better the chance of a
quicker resolution:

-   Software version
-   Platform (OS and JRE)
-   Stack traces generally really help! If in doubt, include the whole thing;
    often exceptions get wrapped in other exceptions and the exception right
    near the bottom explains the actual error, not the first few lines at the
    top. It's very easy for us to skim-read past unnecessary parts of a stack
    trace.
-   Log output can be useful too; sometimes enabling DEBUG logging can help;
-   Your code & configuration files are often useful.

## Security - Vulnerability reporting

If you want to report a vulnerability, you must do so on the
[OW2 Issue Tracker](https://jira.ow2.org/browse/AUTHZFORCE/) with _Security
Level_ set to **Private**. Then, if the AuthzForce team can confirm it, they
will change it to **Public** and set a fix version.

## Contributing

### Documentation

The sources for the manuals are located in
[fiware repository](http://github.com/authzforce/fiware/doc).

### Releasing

1.  From the develop branch, prepare a release (example using a HTTP proxy):

```console
$ mvn -Dhttps.proxyHost=proxyhostname -Dhttps.proxyPort=8080 jgitflow:release-start
```

2.  Update the [changelog](CHANGELOG.md) with the new version according to
    keepachangelog.com.
3.  Commit
4.  Perform the software release (example using a HTTP proxy):

    ```console
    $ mvn -Dhttps.proxyHost=proxyhostname -Dhttps.proxyPort=8080 jgitflow:release-finish
    ```

    If, after deployment, the command does not succeed because of some issue with the branches. Fix the issue, then re-run the     same command but with 'noDeploy' option set to true to avoid re-deployment:

    ```console
    $ mvn -Dhttps.proxyHost=proxyhostname -Dhttps.proxyPort=8080 -DnoDeploy=true jgitflow:release-finish
    ```
    
    More info on jgitflow: http://jgitflow.bitbucket.org/
5.  Connect and log in to the OSS Nexus Repository Manager:
    https://oss.sonatype.org/
6.  Go to Staging Profiles and select the pending repository authzforce-\*...
    you just uploaded with `jgitflow:release-finish`
7.  Click the Release button to release to Maven Central.
8.  When the artifacts have been successfully published on Maven Central, follow
    the instructions in the
    [Release section of fiware repository](https://github.com/authzforce/fiware/blob/master/README.md#release).
9.  Build the Dockerfile by triggering Docker automated build on the current
    Github release branch in
    [authzforce-ce-server's Docker repository](https://hub.docker.com/r/authzforce/server/)
    (_Build Settings_). Check the result in _Build Details_.
10.  Update the versions in badges at the top of this file.
11.  Create a release on Github with a description based on the
    [release description template](release.description.tmpl.md), replacing M/m/P
    with the new major/minor/patch versions.

## License

This project is licensed under the terms of GPL v3 except Java classes in
packages `org.ow2.authzforce.webapp.org.apache.cxf.jaxrs.provider.json.utils`
and `org.ow2.authzforce.webapp.org.codehaus.jettison.mapped` which are under
Apache License.

[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Fauthzforce%2Fserver.svg?type=large)](https://app.fossa.io/projects/git%2Bgithub.com%2Fauthzforce%2Fserver?ref=badge_large)

### Are there any legal issues with GPL 3.0? Is it safe for me to use?

There is absolutely no problem in using a product licensed under GPL 3.0. Issues with GPL 
(or AGPL) licenses are mostly related with the fact that different people assign different 
interpretations on the meaning of the term “derivate work” used in these licenses. Due to this,
some people believe that there is a risk in just _using_ software under GPL or AGPL licenses
(even without _modifying_ it).

For the avoidance of doubt, the owners of this software licensed under an GPL 3.0 license  
wish to make a clarifying public statement as follows:

> Please note that software derived as a result of modifying the source code of this
> software in order to fix a bug or incorporate enhancements is considered a derivative 
> work of the product. Software that merely uses or aggregates (i.e. links to) an otherwise 
> unmodified version of existing software is not considered a derivative work, and therefore
> it does not need to be released as under the same license, or even released as open source.
