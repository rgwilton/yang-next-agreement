---
title: "YANG Next Agreement"
abbrev: "YANG Next Agreement"
category: info

docname: draft-ietf-netmod-yang-next-agreement-latest
submissiontype: IETF  # also: "independent", "editorial", "IAB", or "IRTF"
number:
date:
consensus: true
v: 3
area: "Operations and Management"
workgroup: "Network Modeling"
keyword:
 - yang next
 - yang future

venue:
  group: "Network Modeling"
  type: "Working Group"
  mail: "netmod@ietf.org"
  arch: "https://mailarchive.ietf.org/arch/browse/netmod/"
  github: "rgwilton/yang-next-agreement"
  latest: "https://rgwilton.github.io/yang-next-agreement/draft-wilton-netmod-yang-next-agreement.html"

author:
 -
    fullname: "Robert Wilton"
    organization: Cisco
    email: "rwilton@cisco.com"

normative:

informative:

...

--- abstract

The purpose of this document is to discuss and hopefully find agreement on the scope and shape of the next version of YANG.

--- middle

# Introduction

The [YANG Next Issue Tracker](https://github.com/netmod-wg/yang-next/issues/) lists 125 open issues and 35 closed (some perhaps closed because they would have been non-backwards-compatible changes which were not be considered at the time that they were being evaluated).

These issues are of varying size, complexity and importance.  If the contents of the next version of YANG is defined solely by which issues folks would like to work on (i.e., the bazaar method) then there is a risk that we will end up with a potential large and somewhat inconsistent update to the language.  It is unclear whether this would gain consensus within the IETF or widespread traction within the industry.

Hence, this document proposes that attempt to reach clear consensus on what problems a revised version of YANG is intending to solve and for the issues to be categories into 4 sets:

1. issues that must be included in a next version of YANG
1. issues that should be consider for the next version of YANG (if there is sufficient interest)
1. issues that should not be considered for the next version of YANG, but could be considered for future versions.
1. issues that should not be considered further, i.e., implementing them would likely be too harmful for the language because it would make the language too big or complex or the specification would be too long.

It is worth noting that a couple of separate efforts to score issues has been made, e.g., adding meta-data annotations, or closing the issues, but that in itself does not indicate what issues should be worked on.

In addition, it should be noted that {{issue-152}} has proposed a slightly different scoring/classification of issues (by Andy Bierman).

There is no goal to publish this document as an RFC, and probably the issues could be tracked via a github dashboard.  But a document like this may be a good way to ensure that the working group is aware of what changes are proposed and to ensure that there is consensus.

# Proposed changes that should be made to YANG

The set of issues in this section are ones that the authors believe should be added to any future version of YANG.  Many of these issues are clarifications, small/easy additions, or high importance issues.

<!-- portal:summary:start -->

Summary of proposed changes:

- Specification cleanup (5 issues):
  - Move normative XML encoding rules into its own RFC, {{issue-10}}
  - Move NETCONF-specific sections to NETCONF WG documents, {{issue-11}}
  - Remove normative references to RFC 6241, {{issue-12}}
  - Further restrict YANG Unicode to exclude "del" and C0 control characters, {{issue-125}}
  - Apply verified errata and resolve held for document update errata, {{issue-134}}
- Deprecations and removals (2 issues):
  - Deprecate "import by exact revision", {{issue-75}}
  - remove the "anyxml" statement, {{issue-105}}
- Functionality from extension drafts (5 issues):
  - Incorporate/merge RESTCONF's artifact extension (e.g. rc:yang-data), {{issue-8}}
  - Refine YANG versioning, {{issue-45}}
  - Make NACM default-deny-all and default-deny-write built-in statements, {{issue-61}}
  - Require implementation of "status deprecated" data nodes, {{issue-65}}
  - Require that "status obsolete" nodes are not implemented, {{issue-66}}
- Clarifications (13 issues):
  - Clarify YANG "status" keyword usage (e.g., hierarchical), {{issue-27}}
  - Clarify if multiple deviations target the same schema parts, {{issue-44}}
  - clarify NP-containers, {{issue-88}}
  - Clarify definitions of YANG schema tree, vs module schema tree, {{issue-96}}
  - Clarify duplicate revision dates used in revision history, {{issue-99}}
  - Clarify implicit 'case' behavior, {{issue-103}}
  - clarify "instance-required" behavior in typedefs, {{issue-104}}
  - Clarify canonical order in RFC 7950, {{issue-110}}
  - Clarify whether revision-dates must be unique or not, {{issue-111}}
  - Clarify the meaning of properties which have default value, {{issue-115}}
  - Clarify the behavior if the schema nodes in XPath expression are un-supported, {{issue-116}}
  - Injection of circular imports by deviation, {{issue-126}}
  - Consistent default value behavior for operations and validation, {{issue-146}}
- Minor enhancements (11 issues):
  - default to namespace urn:yang:&lt;module-name&gt; ?, {{issue-5}}
  - add 'status' as a sub statement to 'module', {{issue-28}}
  - Tag YANG identity as an intermediate base for classification only, {{issue-33}}
  - Add native support for float/double, {{issue-34}}
  - Preliminary Status, {{issue-59}}
  - deref() function for leafref statements, {{issue-94}}
  - Disallow if-feature statements where enabling the feature removes data nodes from the schema, {{issue-98}}
  - allow 'require-instance' to be refined, {{issue-101}}
  - Relax rules on usage of deprecated or obsolete identifiers, {{issue-128}}
  - Add 'deprecated' statement, {{issue-130}}
  - Allow hexadecimal notation for enum values, {{issue-155}}
- Larger improvements (13 issues):
  - Support media-type specific schema that can be used to model error-info and other mount-points, {{issue-7}}
  - Allow deviation for Identities, {{issue-40}}
  - Introduce critical extensions, {{issue-49}}
  - A general way to add stmts to any part of a schema, {{issue-51}}
  - context-independent encoding of instance-identifiers and identityrefs, {{issue-56}}
  - Currently, list keys are all mandatory - allows default values for keys., {{issue-62}}
  - Introduce support for critical annotations, {{issue-70}}
  - Make submodule "include" statement require a revision date., {{issue-76}}
  - enable a server express conformance to a set of identifiers, {{issue-80}}
  - Allow an module import to be defined as "types only", {{issue-95}}
  - Add support for hierarchical default values, {{issue-121}}
  - Error statement for actions rpcs, {{issue-149}}
  - Add ability to remove nodes from a grouping in a "uses" statement, {{issue-154}}

In total, 49 of the 160 YANG Next issues are proposed for inclusion.

Is any NMDA (RFC 8342) cleanup needed (**no github tracking issue**)

<!-- portal:summary:end -->

## Clarifications {#clarifications}

All of these issues are applying clarifications that are not intended to change the behaviour in the base specification, just make the specification clearer.  For some of these issues, after review, the conclusion may be that no changes are needed.

<!-- portal:table:start -->

| Issue | Consideration Reason |
| - | - |
| {{issue-27}} Clarify YANG "status" keyword usage (e.g., hierarchical) ([portal](https://yang2.wilton.org.uk/issues/27)) | Initial classification from the draft Clarifications table. |
| {{issue-44}} Clarify if multiple deviations target the same schema parts ([portal](https://yang2.wilton.org.uk/issues/44)) | Initial classification from the draft Clarifications table. |
| {{issue-88}} clarify NP-containers ([portal](https://yang2.wilton.org.uk/issues/88)) | Initial classification from the draft Clarifications table. |
| {{issue-96}} Clarify definitions of YANG schema tree, vs module schema tree ([portal](https://yang2.wilton.org.uk/issues/96)) | Initial classification from the draft Clarifications table. |
| {{issue-99}} Clarify duplicate revision dates used in revision history ([portal](https://yang2.wilton.org.uk/issues/99)) | Initial classification from the draft Clarifications table. |
| {{issue-103}} Clarify implicit 'case' behavior ([portal](https://yang2.wilton.org.uk/issues/103)) | Initial classification from the draft Clarifications table. |
| {{issue-104}} clarify "instance-required" behavior in typedefs ([portal](https://yang2.wilton.org.uk/issues/104)) | Initial classification from the draft Clarifications table. |
| {{issue-110}} Clarify canonical order in RFC 7950 ([portal](https://yang2.wilton.org.uk/issues/110)) | Initial classification from the draft Clarifications table. |
| {{issue-111}} Clarify whether revision-dates must be unique or not ([portal](https://yang2.wilton.org.uk/issues/111)) | Initial classification from the draft Clarifications table. |
| {{issue-115}} Clarify the meaning of properties which have default value ([portal](https://yang2.wilton.org.uk/issues/115)) | Initial classification from the draft Clarifications table. |
| {{issue-116}} Clarify the behavior if the schema nodes in XPath expression are un-supported ([portal](https://yang2.wilton.org.uk/issues/116)) | Initial classification from the draft Clarifications table. |
| {{issue-126}} Injection of circular imports by deviation ([portal](https://yang2.wilton.org.uk/issues/126)) | Initial classification from the draft Clarifications table. |
| {{issue-146}} Consistent default value behavior for operations and validation ([portal](https://yang2.wilton.org.uk/issues/146)) | Initial classification from the draft Clarifications table. |
{: title="Clarifications"}

<!-- portal:table:end -->

## Minor enhancements {#minor-enhancements}

This list of changes is for relatively minor/isolated enhancements.  I.e., the changes are expected to be more isolated, or eady to add.

<!-- portal:table:start -->

| Issue | Consideration Reason |
| - | - |
| {{issue-5}} default to namespace urn:yang:&lt;module-name&gt; ? ([portal](https://yang2.wilton.org.uk/issues/5)) | Initial classification from the draft Minor enhancements table. |
| {{issue-28}} add 'status' as a sub statement to 'module' ([portal](https://yang2.wilton.org.uk/issues/28)) | Initial classification from the draft Minor enhancements table. |
| {{issue-33}} Tag YANG identity as an intermediate base for classification only ([portal](https://yang2.wilton.org.uk/issues/33)) | Initial classification from the draft Minor enhancements table. |
| {{issue-34}} Add native support for float/double ([portal](https://yang2.wilton.org.uk/issues/34)) | Initial classification from the draft Minor enhancements table. |
| {{issue-59}} Preliminary Status ([portal](https://yang2.wilton.org.uk/issues/59)) | Initial classification from the draft Minor enhancements table. |
| {{issue-94}} deref() function for leafref statements ([portal](https://yang2.wilton.org.uk/issues/94)) | Initial classification from the draft Minor enhancements table. |
| {{issue-98}} Disallow if-feature statements where enabling the feature removes data nodes from the schema ([portal](https://yang2.wilton.org.uk/issues/98)) | Initial classification from the draft Minor enhancements table. |
| {{issue-101}} allow 'require-instance' to be refined ([portal](https://yang2.wilton.org.uk/issues/101)) | Initial classification from the draft Minor enhancements table. |
| {{issue-128}} Relax rules on usage of deprecated or obsolete identifiers ([portal](https://yang2.wilton.org.uk/issues/128)) | Initial classification from the draft Minor enhancements table. |
| {{issue-130}} Add 'deprecated' statement ([portal](https://yang2.wilton.org.uk/issues/130)) | Initial classification from the draft Minor enhancements table. |
| {{issue-155}} Allow hexadecimal notation for enum values ([portal](https://yang2.wilton.org.uk/issues/155)) | Initial classification from the draft Minor enhancements table. |
{: title="Minor Enhancements"}

<!-- portal:table:end -->

## Larger improvements {#larger-improvements}

This list of issues tracks more important issues that will require more work, discussion, (and probably text) to specify but are deemed to be important to help grow the language.

<!-- portal:table:start -->

| Issue | Consideration Reason |
| - | - |
| {{issue-7}} Support media-type specific schema that can be used to model error-info and other mount-points ([portal](https://yang2.wilton.org.uk/issues/7)) | Initial classification from the draft Larger improvements table. |
| {{issue-40}} Allow deviation for Identities ([portal](https://yang2.wilton.org.uk/issues/40)) | Initial classification from the draft Larger improvements table. |
| {{issue-49}} Introduce critical extensions ([portal](https://yang2.wilton.org.uk/issues/49)) | Initial classification from the draft Larger improvements table. |
| {{issue-51}} A general way to add stmts to any part of a schema ([portal](https://yang2.wilton.org.uk/issues/51)) | Initial classification from the draft Larger improvements table. |
| {{issue-56}} context-independent encoding of instance-identifiers and identityrefs ([portal](https://yang2.wilton.org.uk/issues/56)) | Initial classification from the draft Larger improvements table. |
| {{issue-62}} Currently, list keys are all mandatory - allows default values for keys. ([portal](https://yang2.wilton.org.uk/issues/62)) | Initial classification from the draft Larger improvements table. |
| {{issue-70}} Introduce support for critical annotations ([portal](https://yang2.wilton.org.uk/issues/70)) | Initial classification from the draft Larger improvements table. |
| {{issue-76}} Make submodule "include" statement require a revision date. ([portal](https://yang2.wilton.org.uk/issues/76)) | Initial classification from the draft Larger improvements table. |
| {{issue-80}} enable a server express conformance to a set of identifiers ([portal](https://yang2.wilton.org.uk/issues/80)) | Initial classification from the draft Larger improvements table. |
| {{issue-95}} Allow an module import to be defined as "types only" ([portal](https://yang2.wilton.org.uk/issues/95)) | Initial classification from the draft Larger improvements table. |
| {{issue-121}} Add support for hierarchical default values ([portal](https://yang2.wilton.org.uk/issues/121)) | Initial classification from the draft Larger improvements table. |
| {{issue-149}} Error statement for actions rpcs ([portal](https://yang2.wilton.org.uk/issues/149)) | Initial classification from the draft Larger improvements table. |
| {{issue-154}} Add ability to remove nodes from a grouping in a "uses" statement ([portal](https://yang2.wilton.org.uk/issues/154)) | Initial classification from the draft Larger improvements table. |
{: title="Larger Improvements"}

<!-- portal:table:end -->

# Proposed issues to consider for the next version of YANG

This list includes items that require further evaluation.  Once fully evaluated all items should end up in one of the other lists, i.e., either we plan on making the change in the next version of YANG or we don't.

<!-- portal:table:start -->

| Issue | Consideration Reason |
| - | - |
| {{issue-2}} Allow if-feature-stmt inside deviation-stmt ([portal](https://yang2.wilton.org.uk/issues/2)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-4}} Add a "map" statement ([portal](https://yang2.wilton.org.uk/issues/4)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-13}} Modify usage examples to be less NETCONF focused ([portal](https://yang2.wilton.org.uk/issues/13)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-16}} Allow when in action ([portal](https://yang2.wilton.org.uk/issues/16)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-21}} Restrict regex to a subset of XML regex specification ([portal](https://yang2.wilton.org.uk/issues/21)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-26}} Consider removing support for sub modules from YANG ([portal](https://yang2.wilton.org.uk/issues/26)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-29}} Clarify YANG validation for data nodes 'decoded' from anydata to a tree of real nodes ([portal](https://yang2.wilton.org.uk/issues/29)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-30}} key-predicate-expr should be (quoted-string / integer-value / decimal-value) ([portal](https://yang2.wilton.org.uk/issues/30)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-31}} Add 'clone' statement ([portal](https://yang2.wilton.org.uk/issues/31)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-32}} Allow Augmentation to Groupings ([portal](https://yang2.wilton.org.uk/issues/32)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-36}} enable leafrefs to uniquely reference a nested list ([portal](https://yang2.wilton.org.uk/issues/36)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-39}} Allow deviation for description ([portal](https://yang2.wilton.org.uk/issues/39)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-41}} Allow some references to from config-true to config-false (add capabilities) ([portal](https://yang2.wilton.org.uk/issues/41)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-46}} Binary encoding support (lets some types have binary persistence) ([portal](https://yang2.wilton.org.uk/issues/46)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-57}} introduce if-module ([portal](https://yang2.wilton.org.uk/issues/57)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-58}} Introduce XPath function datastore() ([portal](https://yang2.wilton.org.uk/issues/58)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-60}} Allowing module private groupings, typedefs ([portal](https://yang2.wilton.org.uk/issues/60)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-63}} Should it be possible to deviate "status"? ([portal](https://yang2.wilton.org.uk/issues/63)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-68}} Add if-feature on must stmt (removed "on import stmt" part) ([portal](https://yang2.wilton.org.uk/issues/68)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-69}} Clarify 'deviation' substatements to match ABNF grammar ([portal](https://yang2.wilton.org.uk/issues/69)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-72}} Introduce an annotation that resolves the union member ([portal](https://yang2.wilton.org.uk/issues/72)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-77}} Add support for a tuple type ([portal](https://yang2.wilton.org.uk/issues/77)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-78}} allow 'must' as a sub statement to 'grouping' ([portal](https://yang2.wilton.org.uk/issues/78)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-81}} let 'description' be a substatement to 'input' and 'output' ([portal](https://yang2.wilton.org.uk/issues/81)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-83}} Clarify canonical representation of typedefs ([portal](https://yang2.wilton.org.uk/issues/83)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-84}} allow notifications/actions to appear in invalid contexts ([portal](https://yang2.wilton.org.uk/issues/84)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-87}} enable specifying augmentation's location amongst siblings ([portal](https://yang2.wilton.org.uk/issues/87)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-90}} Have a mechanism to allow enums to be extended ([portal](https://yang2.wilton.org.uk/issues/90)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-92}} enable \`if-feature\` statements to be "refined" into notifications and actions ([portal](https://yang2.wilton.org.uk/issues/92)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-97}} enable 'ordered-by' to be refined into a list or leaf-list ([portal](https://yang2.wilton.org.uk/issues/97)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-100}} Add errata-stmt to YANG ([portal](https://yang2.wilton.org.uk/issues/100)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-106}} Allow "input/output" to be defined without any child data nodes ([portal](https://yang2.wilton.org.uk/issues/106)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-108}} Allow when in notification ([portal](https://yang2.wilton.org.uk/issues/108)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-109}} Change and compatibility rules for config=false (state) data ([portal](https://yang2.wilton.org.uk/issues/109)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-122}} Changing an identity base ([portal](https://yang2.wilton.org.uk/issues/122)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-129}} Enable reusability without groupings ([portal](https://yang2.wilton.org.uk/issues/129)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-131}} make \`annotation\` a built-in YANG statement ([portal](https://yang2.wilton.org.uk/issues/131)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-133}} support for more efficient CBOR representation of strings ([portal](https://yang2.wilton.org.uk/issues/133)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-138}} Add new node type 'listref' ([portal](https://yang2.wilton.org.uk/issues/138)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-139}} Clarify adding mandatory nodes with augment + when ([portal](https://yang2.wilton.org.uk/issues/139)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-143}} Allow deviation-stmt within uses-stmt ([portal](https://yang2.wilton.org.uk/issues/143)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
| {{issue-144}} YANG 1.1 translation ([portal](https://yang2.wilton.org.uk/issues/144)) | Initial classification from the draft section: proposed issues to consider for the next version of YANG. |
{: title="Possible issue for next YANG version"}

<!-- portal:table:end -->

# Open issues that should not be made for the next version of YANG

I think that the biggest challenges or potential long term enhancements to YANG are:

- replacing XPath with a YANG specific expression language.
- having a cleaner way of handling lists with multiple keys, e.g., when referenced
- a generic way to augment additional statements into a YANG module.
- more advanced or flexible reuse beyond groupings, including perhaps the ability to augment into a grouping.


<!-- portal:table:start -->

| Issue | Consideration Reason |
| - | - |
| {{issue-14}} Allow deviations to modify "when" statements ([portal](https://yang2.wilton.org.uk/issues/14)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-15}} Incorporate/merge RFC 7952 (yang-metadata) ([portal](https://yang2.wilton.org.uk/issues/15)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-18}} add a templating mechanism? ([portal](https://yang2.wilton.org.uk/issues/18)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-19}} yang canonical integer format ([portal](https://yang2.wilton.org.uk/issues/19)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-22}} Restrict usage to a subset of XPATH ([portal](https://yang2.wilton.org.uk/issues/22)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-48}} when using a grouping, the designer should be able to modify just about any aspect of the grouping ([portal](https://yang2.wilton.org.uk/issues/48)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-53}} Create a way for a statement to tie-in with augment/deviation ([portal](https://yang2.wilton.org.uk/issues/53)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-54}} Define a way for extensions to declare sub-statement validity ([portal](https://yang2.wilton.org.uk/issues/54)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-55}} define an encoding-independent "ypath:1.0" type ([portal](https://yang2.wilton.org.uk/issues/55)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-71}} extension-stmt conformance ([portal](https://yang2.wilton.org.uk/issues/71)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-73}} Initial value ([portal](https://yang2.wilton.org.uk/issues/73)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-74}} Support for unique leaf (or leaf combo) in a list of lists ([portal](https://yang2.wilton.org.uk/issues/74)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-82}} enable features to be supported per grouping-use (not globally per-datastore) ([portal](https://yang2.wilton.org.uk/issues/82)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-86}} allow 'case' as a substatement to 'grouping' ([portal](https://yang2.wilton.org.uk/issues/86)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-89}} add 'recommended-default' statement ([portal](https://yang2.wilton.org.uk/issues/89)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-91}} Consider relaxing identity uniqueness to only require uniqueness with the base identity hierarchy ([portal](https://yang2.wilton.org.uk/issues/91)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-93}} support 'dynamic default' ([portal](https://yang2.wilton.org.uk/issues/93)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-113}} Treat if-feature which references a non-existing feature as valid YANG but not enabled ([portal](https://yang2.wilton.org.uk/issues/113)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-119}} The next step of XPath ([portal](https://yang2.wilton.org.uk/issues/119)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-123}} Allow identities to be active even when module is not implemented ([portal](https://yang2.wilton.org.uk/issues/123)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-127}} Relax rules for identityref representation without a prefix ([portal](https://yang2.wilton.org.uk/issues/127)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-147}} New default-system statement to indicate that the default value is not constant (determined by the system) ([portal](https://yang2.wilton.org.uk/issues/147)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-148}} YANG++ ([portal](https://yang2.wilton.org.uk/issues/148)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-150}} nbc-change-stmt ([portal](https://yang2.wilton.org.uk/issues/150)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-153}} Allow 'when' to be a child of the 'refine' statement ([portal](https://yang2.wilton.org.uk/issues/153)) | Initial classification from the draft section: possible issues for a future YANG version. |
| {{issue-156}} canonical forms for strings (aka: the mac-address issue) ([portal](https://yang2.wilton.org.uk/issues/156)) | Initial classification from the draft section: possible issues for a future YANG version. |
{: title="Possible issues for a future YANG version (not the next one)"}

<!-- portal:table:end -->

# Closed issues

This set of issues are either already closed or the author believes that they should be closed and should not be consider further for any future version of YANG.

Issues may be put in this section for any of these reasons:

- the issue is already marked as closed in github, not some closed issues are present on the previous lists because the authors believe that circumstances have changed (i.e., a YANG 2.0 version might be an option) and the issues should be reconsidered.
- the issue is a duplicate
- the issue is due to a misunderstanding of the YANG language or specification
- the consensus is that adding/introducing this issue would be harmful to the YANG language, perhaps taking it too far from its goals, or introducing too much complexity.
- this issue relates to the protocols not the language and hence the issue should be moved to a network protocol issue tracker.

## Open issues proposed for closure in github

This list of issues are not currently marked as being closed in github, but the author is proposing that we close them, in that we *currently* do not consider them for implementation in any YANG version.

<!-- portal:table:start -->

| Issue | Consideration Reason |
| - | - |
| {{issue-6}} Provide a correct ABNF for Yang strings ([portal](https://yang2.wilton.org.uk/issues/6)) | Initial classification from the draft section: open issues proposed for closure. |
| {{issue-9}} Add an “inactive” metadata annotation ([portal](https://yang2.wilton.org.uk/issues/9)) | Initial classification from the draft section: already closed issues. |
| {{issue-17}} replace 'encoding' with 'representation'? ([portal](https://yang2.wilton.org.uk/issues/17)) | Initial classification from the draft section: open issues proposed for closure. |
| {{issue-102}} ascii vs. unicode strings ([portal](https://yang2.wilton.org.uk/issues/102)) | Initial classification from the draft section: open issues proposed for closure. |
| {{issue-107}} Add deviate(not-supported) support for identities ([portal](https://yang2.wilton.org.uk/issues/107)) | Initial classification from the draft section: open issues proposed for closure. |
| {{issue-112}} Support for conditional default values ([portal](https://yang2.wilton.org.uk/issues/112)) | Initial classification from the draft section: open issues proposed for closure. |
| {{issue-117}} Add dynamic feature to YANG ([portal](https://yang2.wilton.org.uk/issues/117)) | Initial classification from the draft section: open issues proposed for closure. |
| {{issue-124}} Add ability to deviate or change status of an identity ([portal](https://yang2.wilton.org.uk/issues/124)) | Initial classification from the draft section: open issues proposed for closure. |
| {{issue-135}} Make YANG Semver real statements instead of extensions ([portal](https://yang2.wilton.org.uk/issues/135)) | Initial classification from the draft section: open issues proposed for closure. |
| {{issue-136}} Revise Module Update Rules ([portal](https://yang2.wilton.org.uk/issues/136)) | Initial classification from the draft section: open issues proposed for closure. |
| {{issue-140}} grouping usage requirements ([portal](https://yang2.wilton.org.uk/issues/140)) | Initial classification from the draft section: open issues proposed for closure. |
| {{issue-141}} Add automatic list key generation (autokey) ([portal](https://yang2.wilton.org.uk/issues/141)) | Initial classification from the draft section: open issues proposed for closure. |
| {{issue-145}} Let a presence container have a default presence? ([portal](https://yang2.wilton.org.uk/issues/145)) | Initial classification from the draft section: open issues proposed for closure. |
| {{issue-151}} YANG profiles and views ([portal](https://yang2.wilton.org.uk/issues/151)) | Initial classification from the draft section: open issues proposed for closure. |
{: title="Currently open issues proposed for closure"}

<!-- portal:table:end -->

## Already closed issued in Github


This follow list of issues are those already marked as being closed in the YANG Next Github tracker.

<!-- portal:table:start -->

| Issue | Consideration Reason |
| - | - |
| {{issue-1}} Only one idea per issue please! ([portal](https://yang2.wilton.org.uk/issues/1)) |  |
| {{issue-3}} Allow prefix statement to be optional for modules that don't need it ([portal](https://yang2.wilton.org.uk/issues/3)) | Initial classification from the draft section: already closed issues. |
| {{issue-20}} add explicit module version-stmt ([portal](https://yang2.wilton.org.uk/issues/20)) | Initial classification from the draft section: already closed issues. |
| {{issue-23}} add 'conformance-type' leaf to 'import' statement ([portal](https://yang2.wilton.org.uk/issues/23)) | Initial classification from the draft section: already closed issues. |
| {{issue-24}} add a 'backwards-compatibility' leaf to 'revision' statement? ([portal](https://yang2.wilton.org.uk/issues/24)) | Initial classification from the draft section: already closed issues. |
| {{issue-25}} make yang more object-oriented ([portal](https://yang2.wilton.org.uk/issues/25)) | Initial classification from the draft section: already closed issues. |
| {{issue-35}} Simplify 'when' statement processing ([portal](https://yang2.wilton.org.uk/issues/35)) | Initial classification from the draft section: already closed issues. |
| {{issue-37}} YANG ANBF could be defined in a simpler way ([portal](https://yang2.wilton.org.uk/issues/37)) | Initial classification from the draft section: already closed issues. |
| {{issue-38}} Allow action to be invoked in the context of configuration datastore ([portal](https://yang2.wilton.org.uk/issues/38)) | Initial classification from the draft section: already closed issues. |
| {{issue-42}} make schema-mount extension into a built-in statement ([portal](https://yang2.wilton.org.uk/issues/42)) | Initial classification from the draft section: already closed issues. |
| {{issue-43}} Support for multiple keys in a list ([portal](https://yang2.wilton.org.uk/issues/43)) | Initial classification from the draft section: already closed issues. |
| {{issue-47}} support external module requirements ([portal](https://yang2.wilton.org.uk/issues/47)) | Initial classification from the draft section: already closed issues. |
| {{issue-50}} YANG Packages (multi-module conformance/guidance) ([portal](https://yang2.wilton.org.uk/issues/50)) | Initial classification from the draft section: already closed issues. |
| {{issue-52}} add a statement for modeling checkbox dialogs ([portal](https://yang2.wilton.org.uk/issues/52)) | Initial classification from the draft section: already closed issues. |
| {{issue-64}} Clarify if double quotes are needed around identifiers ([portal](https://yang2.wilton.org.uk/issues/64)) | Initial classification from the draft section: already closed issues. |
| {{issue-67}} clarify "require-instance" property for leafrefs ([portal](https://yang2.wilton.org.uk/issues/67)) | Initial classification from the draft section: already closed issues. |
| {{issue-79}} add 'unique' as a substatement to 'leaf-list' ([portal](https://yang2.wilton.org.uk/issues/79)) | Initial classification from the draft section: already closed issues. |
| {{issue-85}} add "uses" as a sub-statement to "augment" ([portal](https://yang2.wilton.org.uk/issues/85)) | Initial classification from the draft section: already closed issues. |
| {{issue-114}} Don't treat non-exist import module as a error if there is no any effective reference to this import module in current module ([portal](https://yang2.wilton.org.uk/issues/114)) | Initial classification from the draft section: already closed issues. |
| {{issue-118}} Co-existence between YANG1.0 and YANG1.1 ([portal](https://yang2.wilton.org.uk/issues/118)) | Initial classification from the draft section: already closed issues. |
| {{issue-120}} allow more restriction on Identity-ref ([portal](https://yang2.wilton.org.uk/issues/120)) | Initial classification from the draft section: already closed issues. |
| {{issue-132}} Allow config=true list without a key-stmt ([portal](https://yang2.wilton.org.uk/issues/132)) | Initial classification from the draft section: already closed issues. |
| {{issue-137}} Add validation-rules-stmt to identify YANG validation scope ([portal](https://yang2.wilton.org.uk/issues/137)) | Initial classification from the draft section: already closed issues. |
| {{issue-142}} Allow if-feature on deviations ([portal](https://yang2.wilton.org.uk/issues/142)) | Initial classification from the draft section: already closed issues. |
{: title="Already closed issues"}

<!-- portal:table:end -->



<!-- portal:unclassified:start -->

# New and unclassified issues {#unclassified-issues}

These issues have no classification selected in the portal database.

| Issue | Consideration Reason |
| - | - |
| {{issue-152}} YANG-next issue summary ([portal](https://yang2.wilton.org.uk/issues/152)) |  |
| {{issue-157}} Clarify behaviour of validating leafref/instance-identifier in unions ([portal](https://yang2.wilton.org.uk/issues/157)) |  |
| {{issue-158}} More feature rich imports ([portal](https://yang2.wilton.org.uk/issues/158)) |  |
| {{issue-159}} Grouping DAG dependecies ([portal](https://yang2.wilton.org.uk/issues/159)) |  |
| {{issue-160}} Type union ([portal](https://yang2.wilton.org.uk/issues/160)) |  |

<!-- portal:unclassified:end -->

# Conventions and Definitions

{::boilerplate bcp14-tagged}


# Security Considerations

N/A.  This document is only intended to help the WG reach consensus on the future direction of YANG and contains no protocol work.

# IANA Considerations

This document has no IANA actions.

# Acknowledgments

TODO acknowledge.

--- back

# List of YANG Next Github Issues

This appendix summarizes issues from the YANG-next issue tracker.  The summarization was created by LLM so it may not be entirely accurate.

<!-- portal:provenance:start -->
Issue titles, labels, status, classifications, and rationales are updated programmatically from the portal database. Existing editorial summaries are retained; new issues include the cached GitHub description without summarization.
<!-- portal:provenance:end -->

## [Issue 1](<https://github.com/netmod-wg/yang-next/issues/1>)  {#issue-1}
{:numbered="false"}
**Only one idea per issue please!**

Requested that issues be split so each issue covers one idea to ease prioritization and tracking. Closed as a process note rather than a YANG language issue.

<!-- portal:metadata:start -->

- **Github Labels**: Not a YANG Issue
- **Status**: closed
- **Portal**: [Issue 1](https://yang2.wilton.org.uk/issues/1)
- **Classification**: Do not pursue
- **Rationale**: No rationale provided.

<!-- portal:metadata:end -->

## [Issue 2](<https://github.com/netmod-wg/yang-next/issues/2>)  {#issue-2}
{:numbered="false"}
**Allow if-feature-stmt inside deviation-stmt**

Customers have complained that each platform/revision needs its own deviation file. They would like to define features matching platforms and have 1 deviations file per product family.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-med, backcompat-high, importance-med
- **Status**: open
- **Portal**: [Issue 2](https://yang2.wilton.org.uk/issues/2)
- **Classification**: Consider for YANG 2.0
- **Subgroup**: Minor enhancement
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 3](<https://github.com/netmod-wg/yang-next/issues/3>)  {#issue-3}
{:numbered="false"}
**Allow prefix statement to be optional for modules that don't need it**

Proposed making the prefix statement optional when a module does not reference its own prefix. Objections noted parser ambiguity and the benefit of consistent prefixes across imports. Closed.

<!-- portal:metadata:start -->

- **Github Labels**: Undesirable outcome
- **Status**: closed
- **Portal**: [Issue 3](https://yang2.wilton.org.uk/issues/3)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: already closed issues.

<!-- portal:metadata:end -->

## [Issue 4](<https://github.com/netmod-wg/yang-next/issues/4>)  {#issue-4}
{:numbered="false"}
**Add a "map" statement**

From 9.2. YANG lists as maps YANG has two list constructs, the 'leaf-list' which is similar to a list of scalars (arrays) in other programming languages, and the 'list' which allows a keyed list of complex structures, where the key is also part of the data values.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-high, backcompat-high, importance-med
- **Status**: open
- **Portal**: [Issue 4](https://yang2.wilton.org.uk/issues/4)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 5](<https://github.com/netmod-wg/yang-next/issues/5>)  {#issue-5}
{:numbered="false"}
**default to namespace urn:yang:&lt;module-name&gt; ?**

On 4/20/16, 7:47 AM, "netmod on behalf of Juergen Schoenwaelder" <netmod-bounces@ietf.org on behalf of j.schoenwaelder@jacobs-university.de> wrote: > On Tue, Apr 19, 2016 at 09:37:03PM +0200, Martin Bjorklund wrote: > > But I like `urn:yang:<module-name>` even more - and if we had this, we > wouldn't have to use the "namespace" statement.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-med, backcompat-high, importance-low
- **Status**: open
- **Portal**: [Issue 5](https://yang2.wilton.org.uk/issues/5)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Minor enhancement
- **Rationale**: Initial classification from the draft Minor enhancements table.

<!-- portal:metadata:end -->

## [Issue 6](<https://github.com/netmod-wg/yang-next/issues/6>)  {#issue-6}
{:numbered="false"}
**Provide a correct ABNF for Yang strings**

(This is derived from a mailing list message, The issue that concerns me is that the ABNF doesn't specify what is allowed as a string. I'm used to programming language definitions, where the grammar is specified quite rigidly, to the point that the ABNF can be input to a parser generator.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-low
- **Status**: open
- **Portal**: [Issue 6](https://yang2.wilton.org.uk/issues/6)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: open issues proposed for closure.

<!-- portal:metadata:end -->

## [Issue 7](<https://github.com/netmod-wg/yang-next/issues/7>)  {#issue-7}
{:numbered="false"}
**Support media-type specific schema that can be used to model error-info and other mount-points**

The `<error-info>` element included in NETCONF and RESTCONF is very useful but there is no way to specify YANG schema to match expected data-model specific content. The ietf-restconf module defines the restconf-media-type extension that uses groupings to define this sort of data so they do not appear to be data nodes.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-med, backcompat-high, importance-high
- **Status**: open
- **Portal**: [Issue 7](https://yang2.wilton.org.uk/issues/7)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Larger improvement
- **Rationale**: Initial classification from the draft Larger improvements table.

<!-- portal:metadata:end -->

## [Issue 8](<https://github.com/netmod-wg/yang-next/issues/8>)  {#issue-8}
{:numbered="false"}
**Incorporate/merge RESTCONF's artifact extension (e.g. rc:yang-data)**

The head of the on-list thread is here: But for some reason it doesn't thread-in some responses: Note: the last response does seem to have threading working again, so be sure to check out other responses from others.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-low
- **Status**: open
- **Portal**: [Issue 8](https://yang2.wilton.org.uk/issues/8)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Extension draft functionality
- **Rationale**: Initial classification from the draft summary: fold in keywords/functionality from extension drafts.

<!-- portal:metadata:end -->

## [Issue 9](<https://github.com/netmod-wg/yang-next/issues/9>)  {#issue-9}
{:numbered="false"}
**Add an “inactive” metadata annotation**

The conditional-enablement draft seems to have support, but no one wants to rev NC/RC for it, so adding it into a rev of YANG might be the best option...

<!-- portal:metadata:start -->

- **Github Labels**: importance-med, backcompat-unknown, complexity-unknown, Workaround is better
- **Status**: open
- **Portal**: [Issue 9](https://yang2.wilton.org.uk/issues/9)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: already closed issues.

<!-- portal:metadata:end -->

## [Issue 10](<https://github.com/netmod-wg/yang-next/issues/10>)  {#issue-10}
{:numbered="false"}
**Move normative XML encoding rules into its own RFC**

The YANG RFC is littered with XML encoding rules (see table of contents). These should be factored out into another document like RFC 7951.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-med, backcompat-high, importance-med
- **Status**: closed
- **Portal**: [Issue 10](https://yang2.wilton.org.uk/issues/10)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Specification cleanup
- **Rationale**: Initial classification from the draft summary: cleanup the base specification.

<!-- portal:metadata:end -->

## [Issue 11](<https://github.com/netmod-wg/yang-next/issues/11>)  {#issue-11}
{:numbered="false"}
**Move NETCONF-specific sections to NETCONF WG documents**

The YANG RFC is contains sections entitled "NETCONF XML Encoding Rules" that don't belong in the YANG spec. These should be moved to some future version of 6241, or another draft maintained by the NETCONF WG.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-med, backcompat-high, importance-med
- **Status**: closed
- **Portal**: [Issue 11](https://yang2.wilton.org.uk/issues/11)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Specification cleanup
- **Rationale**: Initial classification from the draft summary: cleanup the base specification.

<!-- portal:metadata:end -->

## [Issue 12](<https://github.com/netmod-wg/yang-next/issues/12>)  {#issue-12}
{:numbered="false"}
**Remove normative references to RFC 6241**

The YANG specification should not have any normative references to RFC 6241. Other than the references from the sections to be removed via issue #11, the only other substantive references are in the Terminology section, which references the following form RFC 6241: o configuration data o configuration datastore o datastore o state data These should probably be moved to the datastore RFC...

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-med
- **Status**: closed
- **Portal**: [Issue 12](https://yang2.wilton.org.uk/issues/12)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Specification cleanup
- **Rationale**: Initial classification from the draft summary: cleanup the base specification.

<!-- portal:metadata:end -->

## [Issue 13](<https://github.com/netmod-wg/yang-next/issues/13>)  {#issue-13}
{:numbered="false"}
**Modify usage examples to be less NETCONF focused**

The Usage Example sections throughout the spec illustrate NETCONF usage. Either these examples should be updated to be equal parts NETCONF and RESTCONF, or they should be removed entirely.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-med
- **Status**: open
- **Portal**: [Issue 13](https://yang2.wilton.org.uk/issues/13)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 14](<https://github.com/netmod-wg/yang-next/issues/14>)  {#issue-14}
{:numbered="false"}
**Allow deviations to modify "when" statements**

Should be self-explanatory; allow deviations to add, remove or replace when statements like they can with must statements.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-med, backcompat-high, importance-med
- **Status**: open
- **Portal**: [Issue 14](https://yang2.wilton.org.uk/issues/14)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 15](<https://github.com/netmod-wg/yang-next/issues/15>)  {#issue-15}
{:numbered="false"}
**Incorporate/merge RFC 7952 (yang-metadata)**

RFC 7952 says: Due to the rules for YANG extensions (see Section 6.3.1 in ), annotation definitions posit relatively weak conformance requirements. The alternative of introducing a new built-in YANG statement for defining annotations was considered, but it was seen as a major change to the language that is inappropriate for YANG 1.1, which was chartered as a maintenance revision.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-low
- **Status**: open
- **Portal**: [Issue 15](https://yang2.wilton.org.uk/issues/15)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 16](<https://github.com/netmod-wg/yang-next/issues/16>)  {#issue-16}
{:numbered="false"}
**Allow when in action**

It would be good to be able to define an action when the value of a certain leaf is set to a (set of) specific value(s). For example: in case we want to reset specific HW components one might think to define a reset action, however certain components (identified by a HW class) might not be supporting a reset.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-med
- **Status**: open
- **Portal**: [Issue 16](https://yang2.wilton.org.uk/issues/16)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 17](<https://github.com/netmod-wg/yang-next/issues/17>)  {#issue-17}
{:numbered="false"}
**replace 'encoding' with 'representation'?**

Discussion starts here: Lada says: > Yes, "representation" should be one of the terms newly defined in 7950bis, and it can also be mentioned that "encoding" was used informally in the past.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-low
- **Status**: open
- **Portal**: [Issue 17](https://yang2.wilton.org.uk/issues/17)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: open issues proposed for closure.

<!-- portal:metadata:end -->

## [Issue 18](<https://github.com/netmod-wg/yang-next/issues/18>)  {#issue-18}
{:numbered="false"}
**add a templating mechanism?**

Templates are not a datastore-specific concept, they can equally well be used in artifacts (static documents)...

<!-- portal:metadata:start -->

- **Github Labels**: complexity-high, backcompat-high, importance-med
- **Status**: open
- **Portal**: [Issue 18](https://yang2.wilton.org.uk/issues/18)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 19](<https://github.com/netmod-wg/yang-next/issues/19>)  {#issue-19}
{:numbered="false"}
**yang canonical integer format**

The canonical format for integer types is the decimal representation. We do not seem to have a mechanism (other than a description statement) to declare that the canonical representation is lets say hexadecimal.

<!-- portal:metadata:start -->

- **Github Labels**: backcompat-high, importance-low, complexity-unknown
- **Status**: open
- **Portal**: [Issue 19](https://yang2.wilton.org.uk/issues/19)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 20](<https://github.com/netmod-wg/yang-next/issues/20>)  {#issue-20}
{:numbered="false"}
**add explicit module version-stmt**

Proposed a module-version statement to avoid parsing revision history for the current version. Considered redundant and addressed by versioning work (issue #45), so closed as duplicate.

<!-- portal:metadata:start -->

- **Github Labels**: Duplicate
- **Status**: closed
- **Portal**: [Issue 20](https://yang2.wilton.org.uk/issues/20)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: already closed issues.

<!-- portal:metadata:end -->

## [Issue 21](<https://github.com/netmod-wg/yang-next/issues/21>)  {#issue-21}
{:numbered="false"}
**Restrict regex to a subset of XML regex specification**

The choice in YANG to use XML regex has effectively meant that there is only a single library implementation of regex parsing. In most cases, the pattern statements only use the basic subset of the XML regex, and normally these pattern statements would validate against most standard regex engines with only a minimal amount of changes (it might be necessary to add anchors at the start and end of the line.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-high, backcompat-low, importance-low
- **Status**: open
- **Portal**: [Issue 21](https://yang2.wilton.org.uk/issues/21)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 22](<https://github.com/netmod-wg/yang-next/issues/22>)  {#issue-22}
{:numbered="false"}
**Restrict usage to a subset of XPATH**

XPATH expressions can be complicated, hard to read, and it is easy to express stuff that (i) implementations don't generally support, or (ii) are not what the author intended. I think that it would be useful to define a subset of XPATH that is allowed/valid, or possibly even consider defining a simpler YANG specific DSL.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-med, backcompat-med, importance-low
- **Status**: open
- **Portal**: [Issue 22](https://yang2.wilton.org.uk/issues/22)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 23](<https://github.com/netmod-wg/yang-next/issues/23>)  {#issue-23}
{:numbered="false"}
**add 'conformance-type' leaf to 'import' statement**

Suggested indicating why a module is imported to aid implementers and catalogs. Comments questioned the problem being solved since tools can already infer usage. Closed.

<!-- portal:metadata:start -->

- **Github Labels**: importance-low, backcompat-unknown, complexity-unknown
- **Status**: closed
- **Portal**: [Issue 23](https://yang2.wilton.org.uk/issues/23)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: already closed issues.

<!-- portal:metadata:end -->

## [Issue 24](<https://github.com/netmod-wg/yang-next/issues/24>)  {#issue-24}
{:numbered="false"}
**add a 'backwards-compatibility' leaf to 'revision' statement?**

Proposed marking non-backwards-compatible changes in revision history to aid clients. Considered part of versioning work (issue #45) and closed as a duplicate.

<!-- portal:metadata:start -->

- **Github Labels**: Duplicate
- **Status**: closed
- **Portal**: [Issue 24](https://yang2.wilton.org.uk/issues/24)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: already closed issues.

<!-- portal:metadata:end -->

## [Issue 25](<https://github.com/netmod-wg/yang-next/issues/25>)  {#issue-25}
{:numbered="false"}
**make yang more object-oriented**

Suggested an object-oriented modeling approach where configuration resembles methods. Commenters felt this would be a different language and referenced limited traction in prior work. Closed.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-high, backcompat-low, importance-low, No support
- **Status**: closed
- **Portal**: [Issue 25](https://yang2.wilton.org.uk/issues/25)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: already closed issues.

<!-- portal:metadata:end -->

## [Issue 26](<https://github.com/netmod-wg/yang-next/issues/26>)  {#issue-26}
{:numbered="false"}
**Consider removing support for sub modules from YANG**

Thread on NETMOD is 'Query about augmenting module from submodule in YANG 1.0'. Juergen: In case YANG 2.0 is ever done, I suggest someone files a proposal to remove submodules if the cost/benefit ratio is at odds.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-low, importance-low
- **Status**: open
- **Portal**: [Issue 26](https://yang2.wilton.org.uk/issues/26)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 27](<https://github.com/netmod-wg/yang-next/issues/27>)  {#issue-27}
{:numbered="false"}
**Clarify YANG "status" keyword usage (e.g., hierarchical)**

This issue requests: Clarify YANG "status" keyword usage (e.g., hierarchical).

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-high
- **Status**: open
- **Portal**: [Issue 27](https://yang2.wilton.org.uk/issues/27)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Clarification
- **Rationale**: Initial classification from the draft Clarifications table.

<!-- portal:metadata:end -->

## [Issue 28](<https://github.com/netmod-wg/yang-next/issues/28>)  {#issue-28}
{:numbered="false"}
**add 'status' as a sub statement to 'module'**

So that an entire module (routing-cfg) can be deprecated w/o having to deprecate every node.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-med
- **Status**: open
- **Portal**: [Issue 28](https://yang2.wilton.org.uk/issues/28)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Minor enhancement
- **Rationale**: Initial classification from the draft Minor enhancements table.

<!-- portal:metadata:end -->

## [Issue 29](<https://github.com/netmod-wg/yang-next/issues/29>)  {#issue-29}
{:numbered="false"}
**Clarify YANG validation for data nodes 'decoded' from anydata to a tree of real nodes**

Raised how offline validation tools should interpret anydata/anyxml in RPC inputs or configs and suggested using schema mount. Replies said this is a tooling concern and schema mount is not appropriate. Closed.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, importance-low, Not a YANG Issue, Tooling issue
- **Status**: closed
- **Portal**: [Issue 29](https://yang2.wilton.org.uk/issues/29)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 30](<https://github.com/netmod-wg/yang-next/issues/30>)  {#issue-30}
{:numbered="false"}
**key-predicate-expr should be (quoted-string / integer-value / decimal-value)**

Proposed errata to allow numeric literals in instance-identifier key predicates instead of requiring quoted strings. The errata was withdrawn due to implementation impact, deferring the topic to YANG 2.0. Closed.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-low, importance-low, Not an issue after all
- **Status**: closed
- **Portal**: [Issue 30](https://yang2.wilton.org.uk/issues/30)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 31](<https://github.com/netmod-wg/yang-next/issues/31>)  {#issue-31}
{:numbered="false"}
**Add 'clone' statement**

Suggested a clone statement to copy existing schema nodes instead of defining reusable groupings. Concerns included misuse, clone loops, and increased complexity; the proposal was withdrawn. Closed.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-high, importance-low, No support
- **Status**: closed
- **Portal**: [Issue 31](https://yang2.wilton.org.uk/issues/31)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 32](<https://github.com/netmod-wg/yang-next/issues/32>)  {#issue-32}
{:numbered="false"}
**Allow Augmentation to Groupings**

Proposed augmenting groupings directly so additions apply to all uses, avoiding repeated augment statements. Implementation and safety concerns dominated the discussion. Closed with no support.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-high, backcompat-low, No support
- **Status**: closed
- **Portal**: [Issue 32](https://yang2.wilton.org.uk/issues/32)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 33](<https://github.com/netmod-wg/yang-next/issues/33>)  {#issue-33}
{:numbered="false"}
**Tag YANG identity as an intermediate base for classification only**

Some identity definitions are not intended to be actual values, but rather used as intermediate base definitions. YANG automation tools need a way to identify these nodes in a machine-readable manner.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-high
- **Status**: open
- **Portal**: [Issue 33](https://yang2.wilton.org.uk/issues/33)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Minor enhancement
- **Rationale**: Initial classification from the draft Minor enhancements table.

<!-- portal:metadata:end -->

## [Issue 34](<https://github.com/netmod-wg/yang-next/issues/34>)  {#issue-34}
{:numbered="false"}
**Add native support for float/double**

Add support for IEEE 754 binary floats and doubles. routing-types.yang (RFC 8294) defines bandwidth-ieee-float32, but ends up using a string as the base type (fine for text based encoding schemes, not so great for binary encoding schemes).

<!-- portal:metadata:start -->

- **Github Labels**: complexity-med, backcompat-high, importance-high
- **Status**: open
- **Portal**: [Issue 34](https://yang2.wilton.org.uk/issues/34)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Minor enhancement
- **Rationale**: Initial classification from the draft Minor enhancements table.

<!-- portal:metadata:end -->

## [Issue 35](<https://github.com/netmod-wg/yang-next/issues/35>)  {#issue-35}
{:numbered="false"}
**Simplify 'when' statement processing**

Proposed removing automatic deletion of nodes when when conditions become false due to complexity and NACM concerns. Discussion favored keeping current behavior and noted that offline validation does not involve request processing. Closed.

<!-- portal:metadata:start -->

- **Github Labels**: Undesirable outcome
- **Status**: closed
- **Portal**: [Issue 35](https://yang2.wilton.org.uk/issues/35)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: already closed issues.

<!-- portal:metadata:end -->

## [Issue 36](<https://github.com/netmod-wg/yang-next/issues/36>)  {#issue-36}
{:numbered="false"}
**enable leafrefs to uniquely reference a nested list**

Given the following data model: and a desire to uniquely reference a specific "bar": ` It's not possible unless all the "bar" names are globally unique (not just for the current "foo").

<!-- portal:metadata:start -->

- **Github Labels**: backcompat-high, importance-high, complexity-unknown
- **Status**: open
- **Portal**: [Issue 36](https://yang2.wilton.org.uk/issues/36)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 37](<https://github.com/netmod-wg/yang-next/issues/37>)  {#issue-37}
{:numbered="false"}
**YANG ANBF could be defined in a simpler way**

Suggested a simplified ABNF that separates generic statement structure from argument validation for parser convenience. Responses noted the simplified grammar already exists in section 6.3 and that changing ABNF would be risky for low benefit. Closed.

<!-- portal:metadata:start -->

- **Github Labels**: backcompat-high, importance-low, Undesirable outcome
- **Status**: closed
- **Portal**: [Issue 37](https://yang2.wilton.org.uk/issues/37)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: already closed issues.

<!-- portal:metadata:end -->

## [Issue 38](<https://github.com/netmod-wg/yang-next/issues/38>)  {#issue-38}
{:numbered="false"}
**Allow action to be invoked in the context of configuration datastore**

Requested allowing actions on configuration datastores to support batch edits or complex operations. The discussion debated semantics and usefulness versus existing edit-config capabilities. Closed without adopting the change.

<!-- portal:metadata:start -->

- **Github Labels**: Undesirable outcome
- **Status**: closed
- **Portal**: [Issue 38](https://yang2.wilton.org.uk/issues/38)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: already closed issues.

<!-- portal:metadata:end -->

## [Issue 39](<https://github.com/netmod-wg/yang-next/issues/39>)  {#issue-39}
{:numbered="false"}
**Allow deviation for description**

Deviations can modify the meaning and content of a data node. However it is not possible to update relevant the description statement.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-high
- **Status**: open
- **Portal**: [Issue 39](https://yang2.wilton.org.uk/issues/39)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 40](<https://github.com/netmod-wg/yang-next/issues/40>)  {#issue-40}
{:numbered="false"}
**Allow deviation for Identities**

It should be possible to declare that I do not support specific identities in a module. E.g.

<!-- portal:metadata:start -->

- **Github Labels**: backcompat-high, importance-high, complexity-unknown
- **Status**: open
- **Portal**: [Issue 40](https://yang2.wilton.org.uk/issues/40)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Larger improvement
- **Rationale**: Initial classification from the draft Larger improvements table.

<!-- portal:metadata:end -->

## [Issue 41](<https://github.com/netmod-wg/yang-next/issues/41>)  {#issue-41}
{:numbered="false"}
**Allow some references to from config-true to config-false (add capabilities)**

IN some cases it would be needed to allow referencing config=false data from config=true leafrefs. E.g.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-high, importance-med, backcompat-unknown
- **Status**: open
- **Portal**: [Issue 41](https://yang2.wilton.org.uk/issues/41)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 42](<https://github.com/netmod-wg/yang-next/issues/42>)  {#issue-42}
{:numbered="false"}
**make schema-mount extension into a built-in statement**

Proposed integrating schema-mount as a core language statement. Comments advised gaining more experience and using critical extensions if needed. Closed.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-med, backcompat-low, importance-low, Workaround is better
- **Status**: closed
- **Portal**: [Issue 42](https://yang2.wilton.org.uk/issues/42)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: already closed issues.

<!-- portal:metadata:end -->

## [Issue 43](<https://github.com/netmod-wg/yang-next/issues/43>)  {#issue-43}
{:numbered="false"}
**Support for multiple keys in a list**

Asked for multiple key statements on a list to allow alternate unique access paths. Discussion noted optional keys were rejected and recommended using two lists with a shared grouping, despite drawbacks for config and augment. Closed.

<!-- portal:metadata:start -->

- **Github Labels**: Not a YANG Issue, NETCONF issue
- **Status**: closed
- **Portal**: [Issue 43](https://yang2.wilton.org.uk/issues/43)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: already closed issues.

<!-- portal:metadata:end -->

## [Issue 44](<https://github.com/netmod-wg/yang-next/issues/44>)  {#issue-44}
{:numbered="false"}
**Clarify if multiple deviations target the same schema parts**

This issue tracks the request titled 'Clarify if multiple deviations target the same schema parts'.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-high
- **Status**: open
- **Portal**: [Issue 44](https://yang2.wilton.org.uk/issues/44)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Clarification
- **Rationale**: Initial classification from the draft Clarifications table.

<!-- portal:metadata:end -->

## [Issue 45](<https://github.com/netmod-wg/yang-next/issues/45>)  {#issue-45}
{:numbered="false"}
**Refine YANG versioning**

This issue tracks the request titled 'Refine YANG versioning'.

<!-- portal:metadata:start -->

- **Github Labels**: backcompat-low, importance-high, complexity-unknown
- **Status**: open
- **Portal**: [Issue 45](https://yang2.wilton.org.uk/issues/45)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Extension draft functionality
- **Rationale**: Initial classification from the draft summary: fold in keywords/functionality from extension drafts.

<!-- portal:metadata:end -->

## [Issue 46](<https://github.com/netmod-wg/yang-next/issues/46>)  {#issue-46}
{:numbered="false"}
**Binary encoding support (lets some types have binary persistence)**

The YANG to CBOR encoding algorithms use binary types for some YANG data types. For example, enumerations are sent using the value number and bits are sent as a number with bits set corresponding to the position number.

<!-- portal:metadata:start -->

- **Github Labels**: importance-high, backcompat-unknown, complexity-unknown, Not a YANG Issue, Workaround is better, NETMOD issue, NETCONF issue
- **Status**: open
- **Portal**: [Issue 46](https://yang2.wilton.org.uk/issues/46)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 47](<https://github.com/netmod-wg/yang-next/issues/47>)  {#issue-47}
{:numbered="false"}
**support external module requirements**

Suggested requires/provides statements to express dependencies beyond import (e.g., augment relationships, common type modules). Reviewers indicated this belongs in YANG library or package definitions. Closed.

<!-- portal:metadata:start -->

- **Github Labels**: Not a YANG Issue, NETMOD issue, NETCONF issue
- **Status**: closed
- **Portal**: [Issue 47](https://yang2.wilton.org.uk/issues/47)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: already closed issues.

<!-- portal:metadata:end -->

## [Issue 48](<https://github.com/netmod-wg/yang-next/issues/48>)  {#issue-48}
{:numbered="false"}
**when using a grouping, the designer should be able to modify just about any aspect of the grouping**

Proposed broad modification capabilities for groupings (removing nodes, disabling if-feature) instead of copy/paste. Concerns focused on complexity and layering effects. Closed as an undesirable outcome.

<!-- portal:metadata:start -->

- **Github Labels**: importance-low, backcompat-unknown, complexity-unknown, Undesirable outcome
- **Status**: closed
- **Portal**: [Issue 48](https://yang2.wilton.org.uk/issues/48)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 49](<https://github.com/netmod-wg/yang-next/issues/49>)  {#issue-49}
{:numbered="false"}
**Introduce critical extensions**

Critical extensions would be allowed to extend or modify YANG semantics and would be mandatory to implement. In fact, some existing extensions, such as **yang-data**, are already of this category, even though RFC 7950 only permits extensions to address *non-YANG semantics*.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-high, importance-med, backcompat-unknown
- **Status**: open
- **Portal**: [Issue 49](https://yang2.wilton.org.uk/issues/49)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Larger improvement
- **Rationale**: Initial classification from the draft Larger improvements table.

<!-- portal:metadata:end -->

## [Issue 50](<https://github.com/netmod-wg/yang-next/issues/50>)  {#issue-50}
{:numbered="false"}
**YANG Packages (multi-module conformance/guidance)**

Requested a machine-readable way to define packages of modules with version/feature requirements. Discussion suggested this is better handled by YANG library or instance data documents and pursued in a separate draft, not the YANG language itself. Closed as not a YANG issue.

<!-- portal:metadata:start -->

- **Github Labels**: Not a YANG Issue, NETMOD issue
- **Status**: closed
- **Portal**: [Issue 50](https://yang2.wilton.org.uk/issues/50)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: already closed issues.

<!-- portal:metadata:end -->

## [Issue 51](<https://github.com/netmod-wg/yang-next/issues/51>)  {#issue-51}
{:numbered="false"}
**A general way to add stmts to any part of a schema**

As a vendor we sometimes want to extend a YANG model by annotating it with extra metadata information. E.g.

<!-- portal:metadata:start -->

- **Github Labels**: backcompat-high, importance-low, complexity-unknown
- **Status**: open
- **Portal**: [Issue 51](https://yang2.wilton.org.uk/issues/51)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Larger improvement
- **Rationale**: Initial classification from the draft Larger improvements table.

<!-- portal:metadata:end -->

## [Issue 52](<https://github.com/netmod-wg/yang-next/issues/52>)  {#issue-52}
{:numbered="false"}
**add a statement for modeling checkbox dialogs**

Proposed a statement to model multi-select checkbox dialogs, similar to choice for radio buttons. Alternatives included using optional leafs, bits, or UI annotations/overlays. Closed with preference for the overlay/annotation solution.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-low, Workaround is better
- **Status**: closed
- **Portal**: [Issue 52](https://yang2.wilton.org.uk/issues/52)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: already closed issues.

<!-- portal:metadata:end -->

## [Issue 53](<https://github.com/netmod-wg/yang-next/issues/53>)  {#issue-53}
{:numbered="false"}
**Create a way for a statement to tie-in with augment/deviation**

RFC7950 section 7.19 specifically says: ` An extension can allow refinement (see Section 7.13.2) and deviations (Section 7.20.3.2), but the mechanism for how this is defined is outside the scope of this specification.` Via refine this extends to augment and uses.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-med, importance-low, backcompat-unknown, Need Examples / Use Case
- **Status**: open
- **Portal**: [Issue 53](https://yang2.wilton.org.uk/issues/53)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 54](<https://github.com/netmod-wg/yang-next/issues/54>)  {#issue-54}
{:numbered="false"}
**Define a way for extensions to declare sub-statement validity**

RFC7950 leaves this capability out in section 7.19: ` The substatements of an extension are defined by the "extension" statement, using some mechanism outside the scope of this specification. Syntactically, the substatements MUST be YANG statements, including extensions defined using "extension" statements.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-med, backcompat-high, importance-med
- **Status**: open
- **Portal**: [Issue 54](https://yang2.wilton.org.uk/issues/54)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 55](<https://github.com/netmod-wg/yang-next/issues/55>)  {#issue-55}
{:numbered="false"}
**define an encoding-independent "ypath:1.0" type**

Discussed the need for a context-independent encoding of XPath expressions and YANG types, avoiding prefix-to-namespace reliance. The conclusion was that this can be addressed in RFC6991bis by making XPath 1.0 context independent, without changing YANG. Closed.

<!-- portal:metadata:start -->

- **Github Labels**: Workaround is better, Undesirable outcome
- **Status**: closed
- **Portal**: [Issue 55](https://yang2.wilton.org.uk/issues/55)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 56](<https://github.com/netmod-wg/yang-next/issues/56>)  {#issue-56}
{:numbered="false"}
**context-independent encoding of instance-identifiers and identityrefs**

In the XML encoding of an instance-identifier or an identityref, there are prefixes that are supposed to be defined in the XML document. This means that there is no canonical format of these values.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-med, backcompat-low, importance-high
- **Status**: open
- **Portal**: [Issue 56](https://yang2.wilton.org.uk/issues/56)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Larger improvement
- **Rationale**: Initial classification from the draft Larger improvements table.

<!-- portal:metadata:end -->

## [Issue 57](<https://github.com/netmod-wg/yang-next/issues/57>)  {#issue-57}
{:numbered="false"}
**introduce if-module**

it would be useful to have a statement "if-module" that can be used like if-feature: leaf my-interface { if-module ietf-interfaces; type if:interface-ref; }.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-unknown, Need Examples / Use Case
- **Status**: open
- **Portal**: [Issue 57](https://yang2.wilton.org.uk/issues/57)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 58](<https://github.com/netmod-wg/yang-next/issues/58>)  {#issue-58}
{:numbered="false"}
**Introduce XPath function datastore()**

The expression datastore(name) would select the root node of datastore name, where name is the name of a datastore. YANG rules for accessible trees are sometimes too limiting.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-high, backcompat-high, importance-low
- **Status**: open
- **Portal**: [Issue 58](https://yang2.wilton.org.uk/issues/58)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 59](<https://github.com/netmod-wg/yang-next/issues/59>)  {#issue-59}
{:numbered="false"}
**Preliminary Status**

Sometimes we deliver features to customers before they are stable, to give them a working preview of functionality. For this we use the following extension statement extension preliminary { description "The schema node is part of an early design effort.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-med
- **Status**: open
- **Portal**: [Issue 59](https://yang2.wilton.org.uk/issues/59)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Minor enhancement
- **Rationale**: Initial classification from the draft Minor enhancements table.

<!-- portal:metadata:end -->

## [Issue 60](<https://github.com/netmod-wg/yang-next/issues/60>)  {#issue-60}
{:numbered="false"}
**Allowing module private groupings, typedefs**

Within a module. top level groupings and typedefs are externally visible and reusable by other modules, whereas local groupings and typedefs are private to the module, and can be changed in a BC way.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-med
- **Status**: open
- **Portal**: [Issue 60](https://yang2.wilton.org.uk/issues/60)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 61](<https://github.com/netmod-wg/yang-next/issues/61>)  {#issue-61}
{:numbered="false"}
**Make NACM default-deny-all and default-deny-write built-in statements**

Ready to promote these to language statements so it is not NACM-specific.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-med
- **Status**: open
- **Portal**: [Issue 61](https://yang2.wilton.org.uk/issues/61)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Extension draft functionality
- **Rationale**: Initial classification from the draft summary: fold in keywords/functionality from extension drafts.

<!-- portal:metadata:end -->

## [Issue 62](<https://github.com/netmod-wg/yang-next/issues/62>)  {#issue-62}
{:numbered="false"}
**Currently, list keys are all mandatory - allows default values for keys.**

This issue tracks the request titled 'Currently, list keys are all mandatory - allows default values for keys.'.

<!-- portal:metadata:start -->

- **Github Labels**: backcompat-high, importance-med, complexity-unknown
- **Status**: open
- **Portal**: [Issue 62](https://yang2.wilton.org.uk/issues/62)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Larger improvement
- **Rationale**: Initial classification from the draft Larger improvements table.

<!-- portal:metadata:end -->

## [Issue 63](<https://github.com/netmod-wg/yang-next/issues/63>)  {#issue-63}
{:numbered="false"}
**Should it be possible to deviate "status"?**

Should it be possible for an implementation to modify the status of a datanode? E.g.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-med, backcompat-high, importance-med
- **Status**: open
- **Portal**: [Issue 63](https://yang2.wilton.org.uk/issues/63)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 64](<https://github.com/netmod-wg/yang-next/issues/64>)  {#issue-64}
{:numbered="false"}
**Clarify if double quotes are needed around identifiers**

Questioned whether identifiers need quotes when used as string arguments (e.g., defaults). Discussion concluded that quoting is optional when no whitespace or special characters are present and that the grammar should remain permissive; pretty printers may normalize. Closed as not a YANG issue.

<!-- portal:metadata:start -->

- **Github Labels**: Not a YANG Issue, NETMOD issue
- **Status**: closed
- **Portal**: [Issue 64](https://yang2.wilton.org.uk/issues/64)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: already closed issues.

<!-- portal:metadata:end -->

## [Issue 65](<https://github.com/netmod-wg/yang-next/issues/65>)  {#issue-65}
{:numbered="false"}
**Require implementation of "status deprecated" data nodes**

We should change YANG (with some sensible migration path) to require servers to implement status deprecated data nodes or otherwise use explicit deviations to indicate that the data nodes are not implemented.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-high
- **Status**: open
- **Portal**: [Issue 65](https://yang2.wilton.org.uk/issues/65)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Extension draft functionality
- **Rationale**: Initial classification from the draft summary: fold in keywords/functionality from extension drafts.

<!-- portal:metadata:end -->

## [Issue 66](<https://github.com/netmod-wg/yang-next/issues/66>)  {#issue-66}
{:numbered="false"}
**Require that "status obsolete" nodes are not implemented**

We should consider changing YANG (with some sensible migration path) to require servers to not implement "status obsolete" nodes or otherwise use a explicit deviation mechanism to indicate that the data noes are still implemented and present in the schema.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-low, importance-med
- **Status**: open
- **Portal**: [Issue 66](https://yang2.wilton.org.uk/issues/66)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Extension draft functionality
- **Rationale**: Initial classification from the draft summary: fold in keywords/functionality from extension drafts.

<!-- portal:metadata:end -->

## [Issue 67](<https://github.com/netmod-wg/yang-next/issues/67>)  {#issue-67}
{:numbered="false"}
**clarify "require-instance" property for leafrefs**

Asked when require-instance is evaluated and how errors are reported for delete operations of referenced nodes. Responses pointed to existing text (sections 8.3.3 and 15.5) covering these cases. Closed with no change required.

<!-- portal:metadata:start -->

- **Github Labels**: Not an issue after all
- **Status**: closed
- **Portal**: [Issue 67](https://yang2.wilton.org.uk/issues/67)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: already closed issues.

<!-- portal:metadata:end -->

## [Issue 68](<https://github.com/netmod-wg/yang-next/issues/68>)  {#issue-68}
{:numbered="false"}
**Add if-feature on must stmt (removed "on import stmt" part)**

When designing the IGMP Proxy model, I feel it needs adding if-feature in import stmt and must stmt. But it doesn't support now.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-med, backcompat-high, importance-low
- **Status**: open
- **Portal**: [Issue 68](https://yang2.wilton.org.uk/issues/68)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 69](<https://github.com/netmod-wg/yang-next/issues/69>)  {#issue-69}
{:numbered="false"}
**Clarify 'deviation' substatements to match ABNF grammar**

As mentioned in: The current table in RFC7950, Section 7.20.3.2 lists all permitted substatements however each argument to "delete" is treated separately per ABNF. If we are not going to add other possible substatements (e.g.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-high
- **Status**: open
- **Portal**: [Issue 69](https://yang2.wilton.org.uk/issues/69)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 70](<https://github.com/netmod-wg/yang-next/issues/70>)  {#issue-70}
{:numbered="false"}
**Introduce support for critical annotations**

Similar to #49, but for annotations (RFC 7952), so that something like Juniper's "inactive" annotation can be introduced without requiring a new version of YANG.

<!-- portal:metadata:start -->

- **Github Labels**: backcompat-unknown, complexity-unknown, importance-unknown, Need Examples / Use Case
- **Status**: open
- **Portal**: [Issue 70](https://yang2.wilton.org.uk/issues/70)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Larger improvement
- **Rationale**: Initial classification from the draft Larger improvements table.

<!-- portal:metadata:end -->

## [Issue 71](<https://github.com/netmod-wg/yang-next/issues/71>)  {#issue-71}
{:numbered="false"}
**extension-stmt conformance**

The conformance requirements and capabilities discovery for extension statements is broken and implementations do not follow it. If a module contains extensions and a server advertises the implemented module in the YANG library, then the server must implement all extensions in the module.

<!-- portal:metadata:start -->

- **Github Labels**: backcompat-unknown, complexity-unknown, importance-unknown, Need Examples / Use Case
- **Status**: open
- **Portal**: [Issue 71](https://yang2.wilton.org.uk/issues/71)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 72](<https://github.com/netmod-wg/yang-next/issues/72>)  {#issue-72}
{:numbered="false"}
**Introduce an annotation that resolves the union member**

Determining the union member to be used for a given instance is a fragile and potentially demanding process that may also depend on the instance representation. The (optional) annotation would pinpoint the member type so that no guesswork is needed.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-med, importance-high, backcompat-unknown
- **Status**: open
- **Portal**: [Issue 72](https://yang2.wilton.org.uk/issues/72)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 73](<https://github.com/netmod-wg/yang-next/issues/73>)  {#issue-73}
{:numbered="false"}
**Initial value**

3GPP is starting to use YANG for modeling. The "default value" as defined by 3GPP is actually an initial value.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-med, importance-med
- **Status**: open
- **Portal**: [Issue 73](https://yang2.wilton.org.uk/issues/73)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 74](<https://github.com/netmod-wg/yang-next/issues/74>)  {#issue-74}
{:numbered="false"}
**Support for unique leaf (or leaf combo) in a list of lists**

If I have a list which has a container, I can specify a leaf to be unique or a combination of leaf nodes to be unique. e.g.: If I have a list of lists, I know of no way to have a leaf inside the lower list to be delcared unique across all lists.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-med, importance-low
- **Status**: open
- **Portal**: [Issue 74](https://yang2.wilton.org.uk/issues/74)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 75](<https://github.com/netmod-wg/yang-next/issues/75>)  {#issue-75}
{:numbered="false"}
**Deprecate "import by exact revision"**

Using YANG import with an exact revision date is invariably the wrong thing to do and I propose that it should be removed from the next version of the language (but allowed for modules using yang-version 1.1).

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, importance-high
- **Status**: open
- **Portal**: [Issue 75](https://yang2.wilton.org.uk/issues/75)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Deprecation or removal
- **Rationale**: Initial classification from the draft summary: deprecations and removals.

<!-- portal:metadata:end -->

## [Issue 76](<https://github.com/netmod-wg/yang-next/issues/76>)  {#issue-76}
{:numbered="false"}
**Make submodule "include" statement require a revision date.**

A particular module revision should be a precisely defined immutable instance of a YANG module. However, allowing submodule includes to not specify the revision of the submodules that they include allows this to be broken.

<!-- portal:metadata:start -->

- **Github Labels**: importance-med, complexity-unknown
- **Status**: open
- **Portal**: [Issue 76](https://yang2.wilton.org.uk/issues/76)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Larger improvement
- **Rationale**: Initial classification from the draft Larger improvements table.

<!-- portal:metadata:end -->

## [Issue 77](<https://github.com/netmod-wg/yang-next/issues/77>)  {#issue-77}
{:numbered="false"}
**Add support for a tuple type**

Sometimes it is desirable to group multiple leaves together into the same value type that is available on a single path. Adding native support for something like a tuple type might be an elegant way of solving this, rather than the current approach of using an adhoc string based tuple type.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-high, importance-high
- **Status**: open
- **Portal**: [Issue 77](https://yang2.wilton.org.uk/issues/77)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 78](<https://github.com/netmod-wg/yang-next/issues/78>)  {#issue-78}
{:numbered="false"}
**allow 'must' as a sub statement to 'grouping'**

The crypto-types:asymmetric-key-pair-grouping grouping is a "container-less" grouping (a grouping that defines its descendents directly, without wrapping them with a container), which makes it impossible to define a 'must' statement that spans all its descendents (none of which are "mandatory true").

<!-- portal:metadata:start -->

- **Github Labels**: importance-low
- **Status**: open
- **Portal**: [Issue 78](https://yang2.wilton.org.uk/issues/78)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 79](<https://github.com/netmod-wg/yang-next/issues/79>)  {#issue-79}
{:numbered="false"}
**add 'unique' as a substatement to 'leaf-list'**

The request sought a unique substatement for leaf-lists. The discussion noted RFC7950 already requires uniqueness for configuration leaf-lists, and the issue was closed as not applicable.

<!-- portal:metadata:start -->

- **Github Labels**: Not an issue after all
- **Status**: closed
- **Portal**: [Issue 79](https://yang2.wilton.org.uk/issues/79)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: already closed issues.

<!-- portal:metadata:end -->

## [Issue 80](<https://github.com/netmod-wg/yang-next/issues/80>)  {#issue-80}
{:numbered="false"}
**enable a server express conformance to a set of identifiers**

This issue requests: enable a server express conformance to a set of identifiers.

<!-- portal:metadata:start -->

- **Github Labels**: importance-high
- **Status**: open
- **Portal**: [Issue 80](https://yang2.wilton.org.uk/issues/80)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Larger improvement
- **Rationale**: Initial classification from the draft Larger improvements table.

<!-- portal:metadata:end -->

## [Issue 81](<https://github.com/netmod-wg/yang-next/issues/81>)  {#issue-81}
{:numbered="false"}
**let 'description' be a substatement to 'input' and 'output'**

It would be nice to have a description statement to describe what a collection of input/output descendent nodes are for.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, importance-med
- **Status**: open
- **Portal**: [Issue 81](https://yang2.wilton.org.uk/issues/81)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 82](<https://github.com/netmod-wg/yang-next/issues/82>)  {#issue-82}
{:numbered="false"}
**enable features to be supported per grouping-use (not globally per-datastore)**

I'm unsure if this is a YANG Next, YANG library, or a modeling issue... Use case: a shared low-level module defines a feature and defines a grouping with an if-feature with it.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-high, importance-low
- **Status**: open
- **Portal**: [Issue 82](https://yang2.wilton.org.uk/issues/82)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 83](<https://github.com/netmod-wg/yang-next/issues/83>)  {#issue-83}
{:numbered="false"}
**Clarify canonical representation of typedefs**

Some typedefs (and possibly some leaves) (e.g. ipv6-prefix) define their own canonical representation of the value space that overrides the canonical representation defined in the YANG built in types (RFC 7950 section 9.1).

<!-- portal:metadata:start -->

- **Github Labels**: importance-high, complexity-unknown
- **Status**: open
- **Portal**: [Issue 83](https://yang2.wilton.org.uk/issues/83)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 84](<https://github.com/netmod-wg/yang-next/issues/84>)  {#issue-84}
{:numbered="false"}
**allow notifications/actions to appear in invalid contexts**

For example, RFC 7950 Section 7.16 says: But sometimes a grouping contains a notification or an action, and thus is currently disqualified from being *used* inside (in this case) a notification.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-high
- **Status**: open
- **Portal**: [Issue 84](https://yang2.wilton.org.uk/issues/84)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 85](<https://github.com/netmod-wg/yang-next/issues/85>)  {#issue-85}
{:numbered="false"}
**add "uses" as a sub-statement to "augment"**

Asked to allow reuse of identical augment blocks via a uses substatement. Closed because this is already supported in YANG 1.1.

<!-- portal:metadata:start -->

- **Github Labels**: none
- **Status**: closed
- **Portal**: [Issue 85](https://yang2.wilton.org.uk/issues/85)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: already closed issues.

<!-- portal:metadata:end -->

## [Issue 86](<https://github.com/netmod-wg/yang-next/issues/86>)  {#issue-86}
{:numbered="false"}
**allow 'case' as a substatement to 'grouping'**

Sometimes there is a need to augment the same case statement into multiple choice statements. Ideally the case statement itself could also be defined in the grouping statement.

<!-- portal:metadata:start -->

- **Github Labels**: importance-low
- **Status**: open
- **Portal**: [Issue 86](https://yang2.wilton.org.uk/issues/86)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 87](<https://github.com/netmod-wg/yang-next/issues/87>)  {#issue-87}
{:numbered="false"}
**enable specifying augmentation's location amongst siblings**

Requested control over the placement of augmented nodes in resolved trees or diagram output. Comments noted YANG is unordered and enforcing order would be a large change; tooling could handle presentation. Closed.

<!-- portal:metadata:start -->

- **Github Labels**: none
- **Status**: closed
- **Portal**: [Issue 87](https://yang2.wilton.org.uk/issues/87)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 88](<https://github.com/netmod-wg/yang-next/issues/88>)  {#issue-88}
{:numbered="false"}
**clarify NP-containers**

Searching the NETCONF list archives for the word "non-presense" found the following: * 2019-06-21: restconf 'get' on non-presence container * 2018-06-28: Existence of Non-Presence Containers * 2016-07-11: What should a server response be?

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, importance-med
- **Status**: open
- **Portal**: [Issue 88](https://yang2.wilton.org.uk/issues/88)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Clarification
- **Rationale**: Initial classification from the draft Clarifications table.

<!-- portal:metadata:end -->

## [Issue 89](<https://github.com/netmod-wg/yang-next/issues/89>)  {#issue-89}
{:numbered="false"}
**add 'recommended-default' statement**

It is important to maintain backward compatibility with existing program behavior. Often the YANG default is not the recommended setting, but rather the setting that matches current program behavior.

<!-- portal:metadata:start -->

- **Github Labels**: importance-low
- **Status**: open
- **Portal**: [Issue 89](https://yang2.wilton.org.uk/issues/89)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 90](<https://github.com/netmod-wg/yang-next/issues/90>)  {#issue-90}
{:numbered="false"}
**Have a mechanism to allow enums to be extended**

I think that in some cases, individuals writing YANG modules end up using identities instead of enums because they want to leave the door open to them being extended in future.

<!-- portal:metadata:start -->

- **Github Labels**: importance-high, complexity-unknown
- **Status**: open
- **Portal**: [Issue 90](https://yang2.wilton.org.uk/issues/90)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 91](<https://github.com/netmod-wg/yang-next/issues/91>)  {#issue-91}
{:numbered="false"}
**Consider relaxing identity uniqueness to only require uniqueness with the base identity hierarchy**

_Flagging this only because it came up as a discussion point on yang-doctors._ Currently identities must be unique within a module. So, in the case of loopback, this meant that I originally defined "loopback-internal", "loopback-line", "loopback-connector" as descendants of base identity "loopback" to avoid namespace collisions.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-high, importance-low
- **Status**: open
- **Portal**: [Issue 91](https://yang2.wilton.org.uk/issues/91)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 92](<https://github.com/netmod-wg/yang-next/issues/92>)  {#issue-92}
{:numbered="false"}
**enable \`if-feature\` statements to be "refined" into notifications and actions**

A grouping can define a data tree, as well as notifications, actions. The refine statement can add if-feature statements to many types of data nodes, but not for actions or notifications.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, importance-high
- **Status**: open
- **Portal**: [Issue 92](https://yang2.wilton.org.uk/issues/92)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 93](<https://github.com/netmod-wg/yang-next/issues/93>)  {#issue-93}
{:numbered="false"}
**support 'dynamic default'**

if a leaf node can not be defined a static default value, but it has a uncertain default value when it is created. For example: If list 'foo' is created, if leaf named 'type''s value is foo1, the default value of leaf named 'value' is 10, if leaf named 'type''s value is foo2, the default value of leaf named 'value' is 20.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-high, importance-low
- **Status**: open
- **Portal**: [Issue 93](https://yang2.wilton.org.uk/issues/93)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 94](<https://github.com/netmod-wg/yang-next/issues/94>)  {#issue-94}
{:numbered="false"}
**deref() function for leafref statements**

This issue requests: deref() function for leafref statements.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, importance-high
- **Status**: open
- **Portal**: [Issue 94](https://yang2.wilton.org.uk/issues/94)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Minor enhancement
- **Rationale**: Initial classification from the draft Minor enhancements table.

<!-- portal:metadata:end -->

## [Issue 95](<https://github.com/netmod-wg/yang-next/issues/95>)  {#issue-95}
{:numbered="false"}
**Allow an module import to be defined as "types only"**

Today, when a module imports another module it isn't made explicit whether that import is to fulfill a path or identity dependency or simply for a typedef dependency. Whilst working on YANG packages, I've began to think that it might be helpful to allow import statements to be annotated to indicate that it only imports type definitions, making the relationship between YANG modules a bit more clear.

<!-- portal:metadata:start -->

- **Github Labels**: importance-high
- **Status**: open
- **Portal**: [Issue 95](https://yang2.wilton.org.uk/issues/95)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Larger improvement
- **Rationale**: Initial classification from the draft Larger improvements table.

<!-- portal:metadata:end -->

## [Issue 96](<https://github.com/netmod-wg/yang-next/issues/96>)  {#issue-96}
{:numbered="false"}
**Clarify definitions of YANG schema tree, vs module schema tree**

The following errata was proposed (but likely rejected) for RFC 7950: Original Text ------------- o schema tree: The definition hierarchy specified within a module. Corrected Text -------------- o schema tree: The hierarchy of schema nodes defined in the set of all modules implemented by a server, as specified in the YANG library data .

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-med
- **Status**: open
- **Portal**: [Issue 96](https://yang2.wilton.org.uk/issues/96)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Clarification
- **Rationale**: Initial classification from the draft Clarifications table.

<!-- portal:metadata:end -->

## [Issue 97](<https://github.com/netmod-wg/yang-next/issues/97>)  {#issue-97}
{:numbered="false"}
**enable 'ordered-by' to be refined into a list or leaf-list**

A grouping may define a list or leaf-list and, for whatever reason, the using model wished to change the 'ordered-by' value. This should be allowed, right?

<!-- portal:metadata:start -->

- **Github Labels**: none
- **Status**: open
- **Portal**: [Issue 97](https://yang2.wilton.org.uk/issues/97)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 98](<https://github.com/netmod-wg/yang-next/issues/98>)  {#issue-98}
{:numbered="false"}
**Disallow if-feature statements where enabling the feature removes data nodes from the schema**

The expressions for if-feature nodes should not be allowed to remove nodes from the tree when a feature is enabled because it makes conformance for clients very complex. E.g.

<!-- portal:metadata:start -->

- **Github Labels**: backcompat-low, importance-unknown
- **Status**: open
- **Portal**: [Issue 98](https://yang2.wilton.org.uk/issues/98)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Minor enhancement
- **Rationale**: Initial classification from the draft Minor enhancements table.

<!-- portal:metadata:end -->

## [Issue 99](<https://github.com/netmod-wg/yang-next/issues/99>)  {#issue-99}
{:numbered="false"}
**Clarify duplicate revision dates used in revision history**

The RFC does not mention any error condition if multiple revision-stmts have the same revision-date value. This occurs in openconfig-inet-types.yang and other real modules.

<!-- portal:metadata:start -->

- **Github Labels**: importance-med
- **Status**: open
- **Portal**: [Issue 99](https://yang2.wilton.org.uk/issues/99)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Clarification
- **Rationale**: Initial classification from the draft Clarifications table.

<!-- portal:metadata:end -->

## [Issue 100](<https://github.com/netmod-wg/yang-next/issues/100>)  {#issue-100}
{:numbered="false"}
**Add errata-stmt to YANG**

There needs to be a way to specify the known Errata corrections to a YANG module. There are many published modules with bugs.

<!-- portal:metadata:start -->

- **Github Labels**: importance-high
- **Status**: open
- **Portal**: [Issue 100](https://yang2.wilton.org.uk/issues/100)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 101](<https://github.com/netmod-wg/yang-next/issues/101>)  {#issue-101}
{:numbered="false"}
**allow 'require-instance' to be refined**

It would be nice to leafref (inside a grouping)'s require-instance property. This to compliment when the list the leafref refers to was itself refined from config true to config false.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-med
- **Status**: open
- **Portal**: [Issue 101](https://yang2.wilton.org.uk/issues/101)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Minor enhancement
- **Rationale**: Initial classification from the draft Minor enhancements table.

<!-- portal:metadata:end -->

## [Issue 102](<https://github.com/netmod-wg/yang-next/issues/102>)  {#issue-102}
{:numbered="false"}
**ascii vs. unicode strings**

Per Alexey Melnikov's DISCUSS on the module-tags draft, though the issue is a long-standing one...

<!-- portal:metadata:start -->

- **Github Labels**: importance-low
- **Status**: open
- **Portal**: [Issue 102](https://yang2.wilton.org.uk/issues/102)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: open issues proposed for closure.

<!-- portal:metadata:end -->

## [Issue 103](<https://github.com/netmod-wg/yang-next/issues/103>)  {#issue-103}
{:numbered="false"}
**Clarify implicit 'case' behavior**

Implicit case statements have some interesting undocumented behavior:.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-med
- **Status**: open
- **Portal**: [Issue 103](https://yang2.wilton.org.uk/issues/103)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Clarification
- **Rationale**: Initial classification from the draft Clarifications table.

<!-- portal:metadata:end -->

## [Issue 104](<https://github.com/netmod-wg/yang-next/issues/104>)  {#issue-104}
{:numbered="false"}
**clarify "instance-required" behavior in typedefs**

As discussed in authors expect that the require-instance statement is available not only for leafref and instance-identifier types, but also for all the types derived from them using typedef statement.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-high
- **Status**: open
- **Portal**: [Issue 104](https://yang2.wilton.org.uk/issues/104)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Clarification
- **Rationale**: Initial classification from the draft Clarifications table.

<!-- portal:metadata:end -->

## [Issue 105](<https://github.com/netmod-wg/yang-next/issues/105>)  {#issue-105}
{:numbered="false"}
**remove the "anyxml" statement**

**Synopsis:** The "anyxml" statement was made in RFC 6020, before JSON support was added, but its value is questionable now... Propose to remove (if YANG-next == YANG 2.0) or deprecate (if YANG-next == YANG 1.2).

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, importance-med, backcompat-unknown
- **Status**: open
- **Portal**: [Issue 105](https://yang2.wilton.org.uk/issues/105)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Deprecation or removal
- **Rationale**: Initial classification from the draft summary: deprecations and removals.

<!-- portal:metadata:end -->

## [Issue 106](<https://github.com/netmod-wg/yang-next/issues/106>)  {#issue-106}
{:numbered="false"}
**Allow "input/output" to be defined without any child data nodes**

Change the ABNF to allow an input and output statements to be defined without any data nodes (e.g. in the case that models are expected to augment the input/output statement).

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-med
- **Status**: open
- **Portal**: [Issue 106](https://yang2.wilton.org.uk/issues/106)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 107](<https://github.com/netmod-wg/yang-next/issues/107>)  {#issue-107}
{:numbered="false"}
**Add deviate(not-supported) support for identities**

There is no way for a server vendor to tell the YANG client that a specific identity in a YANG module is not supported. This is critical for vendors who use YANG modules defined by an SDO (all of them) YANG conformance for identities is particularly awful at this time because it mandates that every identity in a supported module be implemented.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-med, importance-high
- **Status**: open
- **Portal**: [Issue 107](https://yang2.wilton.org.uk/issues/107)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: open issues proposed for closure.

<!-- portal:metadata:end -->

## [Issue 108](<https://github.com/netmod-wg/yang-next/issues/108>)  {#issue-108}
{:numbered="false"}
**Allow when in notification**

It seems the when statement should be supported also for the notification statement with the exact same justification as for action (.

<!-- portal:metadata:start -->

- **Github Labels**: importance-med, backcompat-unknown, complexity-unknown
- **Status**: open
- **Portal**: [Issue 108](https://yang2.wilton.org.uk/issues/108)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 109](<https://github.com/netmod-wg/yang-next/issues/109>)  {#issue-109}
{:numbered="false"}
**Change and compatibility rules for config=false (state) data**

Allowed changes and what is compatible should be specified differently for config=true and config=false data. E.g.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-high, importance-med
- **Status**: open
- **Portal**: [Issue 109](https://yang2.wilton.org.uk/issues/109)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 110](<https://github.com/netmod-wg/yang-next/issues/110>)  {#issue-110}
{:numbered="false"}
**Clarify canonical order in RFC 7950**

RFC 7950 states this: The ABNF grammar defines the canonical order. But the ABNF also has sections that state: ;; these stmts can appear in any order These two statements seem to cause some confusion, and hence it might be helpful to clarify that the ";; these stmts can appear in any order" does not remove the need to list statements in the order listed in the ABNF for them to be regarded as being in canonical order.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-med
- **Status**: open
- **Portal**: [Issue 110](https://yang2.wilton.org.uk/issues/110)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Clarification
- **Rationale**: Initial classification from the draft Clarifications table.

<!-- portal:metadata:end -->

## [Issue 111](<https://github.com/netmod-wg/yang-next/issues/111>)  {#issue-111}
{:numbered="false"}
**Clarify whether revision-dates must be unique or not**

RFC 7950 does not explicitly state whether two different revisions of a module can be published with the same date. There is an example of an OpenConfig YANG module that has published multiple revisions with the same revision date, and the OpenConfig proponents state that RFC 7950 does not disallow this.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-high
- **Status**: open
- **Portal**: [Issue 111](https://yang2.wilton.org.uk/issues/111)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Clarification
- **Rationale**: Initial classification from the draft Clarifications table.

<!-- portal:metadata:end -->

## [Issue 112](<https://github.com/netmod-wg/yang-next/issues/112>)  {#issue-112}
{:numbered="false"}
**Support for conditional default values**

This issue tracks the request titled 'Support for conditional default values'.

<!-- portal:metadata:start -->

- **Github Labels**: importance-low
- **Status**: open
- **Portal**: [Issue 112](https://yang2.wilton.org.uk/issues/112)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: open issues proposed for closure.

<!-- portal:metadata:end -->

## [Issue 113](<https://github.com/netmod-wg/yang-next/issues/113>)  {#issue-113}
{:numbered="false"}
**Treat if-feature which references a non-existing feature as valid YANG but not enabled**

Scenario: A software product comes out with a YANG model. It supports extensions referencing its model.

<!-- portal:metadata:start -->

- **Github Labels**: importance-med
- **Status**: open
- **Portal**: [Issue 113](https://yang2.wilton.org.uk/issues/113)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 114](<https://github.com/netmod-wg/yang-next/issues/114>)  {#issue-114}
{:numbered="false"}
**Don't treat non-exist import module as a error if there is no any effective reference to this import module in current module**

Suggested not requiring imported modules when deviations remove all effective references. Reviewers said this is not implementable because deviations are separate modules and prefixes used at parse time must resolve. Closed as non-implementable.

<!-- portal:metadata:start -->

- **Github Labels**: none
- **Status**: closed
- **Portal**: [Issue 114](https://yang2.wilton.org.uk/issues/114)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: already closed issues.

<!-- portal:metadata:end -->

## [Issue 115](<https://github.com/netmod-wg/yang-next/issues/115>)  {#issue-115}
{:numbered="false"}
**Clarify the meaning of properties which have default value**

Some statements have default value, such as config/mandatory/min-elements/max-elements/status etc. if these statements are not occur under parent nodes, and should we think the parent nodes have these properties actually?

<!-- portal:metadata:start -->

- **Github Labels**: complexity-med, importance-high
- **Status**: open
- **Portal**: [Issue 115](https://yang2.wilton.org.uk/issues/115)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Clarification
- **Rationale**: Initial classification from the draft Clarifications table.

<!-- portal:metadata:end -->

## [Issue 116](<https://github.com/netmod-wg/yang-next/issues/116>)  {#issue-116}
{:numbered="false"}
**Clarify the behavior if the schema nodes in XPath expression are un-supported**

If the schema nodes in XPath expression are un-supported by deviation, should the YANG parser report error? If YANG parser report error, user have to also change the XPath expression by deviation (if its when, it can not be deviated.) If YANG parser dont report error, the evaluation of XPath expression MAY cause unexpected result.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-med, backcompat-high, importance-med
- **Status**: open
- **Portal**: [Issue 116](https://yang2.wilton.org.uk/issues/116)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Clarification
- **Rationale**: Initial classification from the draft Clarifications table.

<!-- portal:metadata:end -->

## [Issue 117](<https://github.com/netmod-wg/yang-next/issues/117>)  {#issue-117}
{:numbered="false"}
**Add dynamic feature to YANG**

YANG provide the capability to define data model statically. But it can not meet the requirements of some scenarios: Some nodes are associated with licenses.

<!-- portal:metadata:start -->

- **Github Labels**: importance-low
- **Status**: open
- **Portal**: [Issue 117](https://yang2.wilton.org.uk/issues/117)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: open issues proposed for closure.

<!-- portal:metadata:end -->

## [Issue 118](<https://github.com/netmod-wg/yang-next/issues/118>)  {#issue-118}
{:numbered="false"}
**Co-existence between YANG1.0 and YANG1.1**

Argued that YANG 1.0 modules should be able to import YANG 1.1 modules and that RFC7950 has protocol-specific language. Responses emphasized that YANG 1.0 semantics must apply to all imports and that new keywords complicate this. Closed with consensus to keep current rules, though protocol wording could be generalized.

<!-- portal:metadata:start -->

- **Github Labels**: none
- **Status**: closed
- **Portal**: [Issue 118](https://yang2.wilton.org.uk/issues/118)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: already closed issues.

<!-- portal:metadata:end -->

## [Issue 119](<https://github.com/netmod-wg/yang-next/issues/119>)  {#issue-119}
{:numbered="false"}
**The next step of XPath**

Scenarios: The expression of when/must of YANG using XPath 1.0 is not readable and error-prone. The XPath filtering of NETCONF has the same problem.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-high, importance-med
- **Status**: open
- **Portal**: [Issue 119](https://yang2.wilton.org.uk/issues/119)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 120](<https://github.com/netmod-wg/yang-next/issues/120>)  {#issue-120}
{:numbered="false"}
**allow more restriction on Identity-ref**

Proposed permit/deny substatements under identityref base to exclude specific derived identities. Discussion favored a simpler restriction mechanism and noted overlap with other issues. Closed as a duplicate.

<!-- portal:metadata:start -->

- **Github Labels**: none
- **Status**: closed
- **Portal**: [Issue 120](https://yang2.wilton.org.uk/issues/120)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: already closed issues.

<!-- portal:metadata:end -->

## [Issue 121](<https://github.com/netmod-wg/yang-next/issues/121>)  {#issue-121}
{:numbered="false"}
**Add support for hierarchical default values**

Hierarchical configuration is often expressed in YANG models. In this model, only the "default" statement should be placed at the root of the hierarchical configuration, and all other nodes in the configuration hierarchy must not have a default statement, and instead are required to state in text that the default value is inherited from the next node up in the hierarchical config chain.

<!-- portal:metadata:start -->

- **Github Labels**: importance-med, Need Examples / Use Case
- **Status**: open
- **Portal**: [Issue 121](https://yang2.wilton.org.uk/issues/121)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Larger improvement
- **Rationale**: Initial classification from the draft Larger improvements table.

<!-- portal:metadata:end -->

## [Issue 122](<https://github.com/netmod-wg/yang-next/issues/122>)  {#issue-122}
{:numbered="false"}
**Changing an identity base**

Since, as explained in section 7.18.2 of RFC7950, the derivation of identities is transitive, replacing a "base" statement with new "base" statement which is derived from the previous one is also a BC change.

<!-- portal:metadata:start -->

- **Github Labels**: importance-med, complexity-unknown
- **Status**: open
- **Portal**: [Issue 122](https://yang2.wilton.org.uk/issues/122)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 123](<https://github.com/netmod-wg/yang-next/issues/123>)  {#issue-123}
{:numbered="false"}
**Allow identities to be active even when module is not implemented**

Currently a module has to be implemented in order for its identities to be active. Why is this so?

<!-- portal:metadata:start -->

- **Github Labels**: importance-high
- **Status**: open
- **Portal**: [Issue 123](https://yang2.wilton.org.uk/issues/123)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 124](<https://github.com/netmod-wg/yang-next/issues/124>)  {#issue-124}
{:numbered="false"}
**Add ability to deviate or change status of an identity**

E.g., for deprecated security protocols, a server should be able to indicate which ones it supports (although this could also be done via a capabilities YANG module).

<!-- portal:metadata:start -->

- **Github Labels**: importance-high
- **Status**: open
- **Portal**: [Issue 124](https://yang2.wilton.org.uk/issues/124)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: open issues proposed for closure.

<!-- portal:metadata:end -->

## [Issue 125](<https://github.com/netmod-wg/yang-next/issues/125>)  {#issue-125}
{:numbered="false"}
**Further restrict YANG Unicode to exclude "del" and C0 control characters**

See Unicode Assignables, section 4.3, that defines the table of Unicode assignable characters. This matches what is in RFC 7950, 'yang-char', page 209, except that YANG allows characters in the range %x20-D7FF, but draft-bray-unichars-07 splits this into two ranges to also exclude C1 control characters and DEL: It would probably be helpful for the next version of YANG to be updated to also exclude these characters, and perhaps reference one of the "Character Repertoires", if that draft-bray-unichars progresses.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low
- **Status**: open
- **Portal**: [Issue 125](https://yang2.wilton.org.uk/issues/125)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Specification cleanup
- **Rationale**: Initial classification from the draft summary: cleanup the base specification.

<!-- portal:metadata:end -->

## [Issue 126](<https://github.com/netmod-wg/yang-next/issues/126>)  {#issue-126}
{:numbered="false"}
**Injection of circular imports by deviation**

RFC 7950 is unclear on whether import dependencies may be introduced by way of deviations. According to RFC 7950: This is clear and I believe most people find this reasonable enough.

<!-- portal:metadata:start -->

- **Github Labels**: importance-high
- **Status**: open
- **Portal**: [Issue 126](https://yang2.wilton.org.uk/issues/126)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Clarification
- **Rationale**: Initial classification from the draft Clarifications table.

<!-- portal:metadata:end -->

## [Issue 127](<https://github.com/netmod-wg/yang-next/issues/127>)  {#issue-127}
{:numbered="false"}
**Relax rules for identityref representation without a prefix**

Problem There are several YANG modules that have must/when XPath that compares an identityref leaf to a string literal. uses terminal-otn-protocol-top { when "config/logical-channel-type = 'PROT_OTN'" { description "Include the OTN protocol data only when the channel is using OTN framing."; } } The strict rules in the RFC (sec 9.10) say that the identity (e.g.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-med
- **Status**: open
- **Portal**: [Issue 127](https://yang2.wilton.org.uk/issues/127)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 128](<https://github.com/netmod-wg/yang-next/issues/128>)  {#issue-128}
{:numbered="false"}
**Relax rules on usage of deprecated or obsolete identifiers**

sec 7.21.2 Issue 1) these MUST requirements make a YANG module brittle and hard to change Issue 2) "deprecated" status should be treated as MUST implement, so "current" using "deprecated" is just a warning Issue 3) "obsolete" status should be treated as MUST NOT implement, but it actually is SHOULD NOT implement.

<!-- portal:metadata:start -->

- **Github Labels**: importance-med
- **Status**: open
- **Portal**: [Issue 128](https://yang2.wilton.org.uk/issues/128)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Minor enhancement
- **Rationale**: Initial classification from the draft Minor enhancements table.

<!-- portal:metadata:end -->

## [Issue 129](<https://github.com/netmod-wg/yang-next/issues/129>)  {#issue-129}
{:numbered="false"}
**Enable reusability without groupings**

The "grouping" statement is great, but only if the module-designer had the foresight to create groupings, which rarely happens. The YANG Full Embed draft attempts to address this by enabling other nodes in a module (e.g., leaf, container) to be used like a grouping.

<!-- portal:metadata:start -->

- **Github Labels**: importance-high
- **Status**: open
- **Portal**: [Issue 129](https://yang2.wilton.org.uk/issues/129)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 130](<https://github.com/netmod-wg/yang-next/issues/130>)  {#issue-130}
{:numbered="false"}
**Add 'deprecated' statement**

A new statement is needed to help enforce professional API lifecycle management. The 'deprecated' statement contains information about the associated object which has a status of 'deprecated' The purpose of the statement is to provide machine and human readable instructions about the deprecation and suggest a replacement description: text why the node is deprecated replaced-by: machine-readable identifier to use instead of this identifier TBD: expected timeframe for obsolete status container foo { status deprecated; deprecated { description "This node replaced by new system module"; replaced-by bar; } }.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-high
- **Status**: open
- **Portal**: [Issue 130](https://yang2.wilton.org.uk/issues/130)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Minor enhancement
- **Rationale**: Initial classification from the draft Minor enhancements table.

<!-- portal:metadata:end -->

## [Issue 131](<https://github.com/netmod-wg/yang-next/issues/131>)  {#issue-131}
{:numbered="false"}
**make \`annotation\` a built-in YANG statement**

RFC 7952 says: An NBC version of YANG-next can do it.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, importance-med
- **Status**: open
- **Portal**: [Issue 131](https://yang2.wilton.org.uk/issues/131)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 132](<https://github.com/netmod-wg/yang-next/issues/132>)  {#issue-132}
{:numbered="false"}
**Allow config=true list without a key-stmt**

Suggested permitting keyless config lists for cases where the list is only created or replaced as a whole. Opponents cited interoperability and quality issues with keyless lists, while others noted autokey expectations. Closed as a duplicate of another issue.

<!-- portal:metadata:start -->

- **Github Labels**: none
- **Status**: closed
- **Portal**: [Issue 132](https://yang2.wilton.org.uk/issues/132)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: already closed issues.

<!-- portal:metadata:end -->

## [Issue 133](<https://github.com/netmod-wg/yang-next/issues/133>)  {#issue-133}
{:numbered="false"}
**support for more efficient CBOR representation of strings**

There are some strings that could be smaller to encode as binary than the UTF8 representation. Strings such as ipv4-address can be encoded more efficiently n binary YANG to CBOR encoding is being enhanced with some new work to encode some strings in CBOR binary format This should not be done with a hard-wired solution.

<!-- portal:metadata:start -->

- **Github Labels**: importance-med
- **Status**: open
- **Portal**: [Issue 133](https://yang2.wilton.org.uk/issues/133)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 134](<https://github.com/netmod-wg/yang-next/issues/134>)  {#issue-134}
{:numbered="false"}
**Apply verified errata and resolve held for document update errata**

There are 14 verified errata RFC 7950 Errata There are 2 held for review Held for Document Update Must Do.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-high
- **Status**: closed
- **Portal**: [Issue 134](https://yang2.wilton.org.uk/issues/134)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Specification cleanup
- **Rationale**: Initial classification from the draft summary: cleanup the base specification.

<!-- portal:metadata:end -->

## [Issue 135](<https://github.com/netmod-wg/yang-next/issues/135>)  {#issue-135}
{:numbered="false"}
**Make YANG Semver real statements instead of extensions**

YANG Semver It is important that "imports" and other procedures directly related to YANG language processing are mandatory to implement so all tools get the correct results. extensions should be real (mandatory-to-implement) statements and not optional extensions no intent to change Semver work file name issues are not be included in this issue Must Do.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-med, importance-med
- **Status**: open
- **Portal**: [Issue 135](https://yang2.wilton.org.uk/issues/135)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: open issues proposed for closure.

<!-- portal:metadata:end -->

## [Issue 136](<https://github.com/netmod-wg/yang-next/issues/136>)  {#issue-136}
{:numbered="false"}
**Revise Module Update Rules**

module update rules Dreaded "Section 11" had caused more trouble than any other section. **NBC Change** New Identifier Not Mandatory WG list discussion has resulted in the following change text change in this section OLD NEW Other refinements may be identified during development Must Do: complexity: medium, bc: low, importance: high.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-med, importance-med
- **Status**: open
- **Portal**: [Issue 136](https://yang2.wilton.org.uk/issues/136)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: open issues proposed for closure.

<!-- portal:metadata:end -->

## [Issue 137](<https://github.com/netmod-wg/yang-next/issues/137>)  {#issue-137}
{:numbered="false"}
**Add validation-rules-stmt to identify YANG validation scope**

Proposed a validation-rules statement with strict/relaxed/off modes to handle offline validation, multi-datastore references, or template-like configuration. Discussion questioned the examples and scope, suggesting use of intended datastores, must statements, or presence-like behavior. Closed per the Oct 24 meeting.

<!-- portal:metadata:start -->

- **Github Labels**: none
- **Status**: closed
- **Portal**: [Issue 137](https://yang2.wilton.org.uk/issues/137)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: already closed issues.

<!-- portal:metadata:end -->

## [Issue 138](<https://github.com/netmod-wg/yang-next/issues/138>)  {#issue-138}
{:numbered="false"}
**Add new node type 'listref'**

This is related to tuples #77 A listref is similar to a leafref but it represents the keys or position that identtify one list instance A listref is a terminal node like a leaf or a leaf-list or anydata A listref works the same no matter how many key leafs are defined for the list **Full Representation** no -keys: position of the list entry keys: leaf matching each leaf in the key-stmt **Short Representation** no-keys: use uint32 keys: use TBD tuple from #77 solution Example target grouping Pointed-at list: Listref A listref value example.

<!-- portal:metadata:start -->

- **Github Labels**: importance-high
- **Status**: open
- **Portal**: [Issue 138](https://yang2.wilton.org.uk/issues/138)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 139](<https://github.com/netmod-wg/yang-next/issues/139>)  {#issue-139}
{:numbered="false"}
**Clarify adding mandatory nodes with augment + when**

augments para 7 example is not valid since old client knows about values 1 .. 5 for toasterWeight.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-med
- **Status**: open
- **Portal**: [Issue 139](https://yang2.wilton.org.uk/issues/139)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 140](<https://github.com/netmod-wg/yang-next/issues/140>)  {#issue-140}
{:numbered="false"}
**grouping usage requirements**

There are some design choices that are possible with YANG groupings that need to be considered for the uses-stmt There are documentation guidelines but these are not commonly followed, or complete.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-med, importance-med
- **Status**: open
- **Portal**: [Issue 140](https://yang2.wilton.org.uk/issues/140)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: open issues proposed for closure.

<!-- portal:metadata:end -->

## [Issue 141](<https://github.com/netmod-wg/yang-next/issues/141>)  {#issue-141}
{:numbered="false"}
**Add automatic list key generation (autokey)**

There are many use-cases where the list key does not contain any semantics other than being a unique identifier. In this mode, the designer does not want to define or implement the list key management.

<!-- portal:metadata:start -->

- **Github Labels**: importance-med
- **Status**: open
- **Portal**: [Issue 141](https://yang2.wilton.org.uk/issues/141)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: open issues proposed for closure.

<!-- portal:metadata:end -->

## [Issue 142](<https://github.com/netmod-wg/yang-next/issues/142>)  {#issue-142}
{:numbered="false"}
**Allow if-feature on deviations**

The issue requested allowing "if-feature" under "deviation" so a single deviation module could cover both feature-enabled and feature-disabled cases. It was closed as a duplicate of issue #2.

<!-- portal:metadata:start -->

- **Github Labels**: none
- **Status**: closed
- **Portal**: [Issue 142](https://yang2.wilton.org.uk/issues/142)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: already closed issues.

<!-- portal:metadata:end -->

## [Issue 143](<https://github.com/netmod-wg/yang-next/issues/143>)  {#issue-143}
{:numbered="false"}
**Allow deviation-stmt within uses-stmt**

Sometimes an existing grouping is almost usable in a new us-case. YANG 1.1 allows some refinements in the 'uses' expansion, but no deviations.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-med, importance-med
- **Status**: open
- **Portal**: [Issue 143](https://yang2.wilton.org.uk/issues/143)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 144](<https://github.com/netmod-wg/yang-next/issues/144>)  {#issue-144}
{:numbered="false"}
**YANG 1.1 translation**

A standard translation from YANG 2.0 to YANG 1.1 should be defined. It will be difficult to introduce YANG 2.0 modules if a YANG 1.1 version cannot be automatically generated for existing tools to use.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, importance-high
- **Status**: open
- **Portal**: [Issue 144](https://yang2.wilton.org.uk/issues/144)
- **Classification**: Consider for YANG 2.0
- **Rationale**: Initial classification from the draft section: proposed issues to consider for the next version of YANG.

<!-- portal:metadata:end -->

## [Issue 145](<https://github.com/netmod-wg/yang-next/issues/145>)  {#issue-145}
{:numbered="false"}
**Let a presence container have a default presence?**

A presence container is like a leaf of type boolean. Leafs can have a default have, but not P-containers, why?

<!-- portal:metadata:start -->

- **Github Labels**: importance-low
- **Status**: open
- **Portal**: [Issue 145](https://yang2.wilton.org.uk/issues/145)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: open issues proposed for closure.

<!-- portal:metadata:end -->

## [Issue 146](<https://github.com/netmod-wg/yang-next/issues/146>)  {#issue-146}
{:numbered="false"}
**Consistent default value behavior for operations and validation**

YANG validation (e.g., must-stmt) sometimes depends on whether a "node exists in the accessible tree". This creates a cornercase with the default-stmt The issue is that every server can decide differently whether node /npcon/B is in the accessible tree.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-high
- **Status**: open
- **Portal**: [Issue 146](https://yang2.wilton.org.uk/issues/146)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Clarification
- **Rationale**: Initial classification from the draft Clarifications table.

<!-- portal:metadata:end -->

## [Issue 147](<https://github.com/netmod-wg/yang-next/issues/147>)  {#issue-147}
{:numbered="false"}
**New default-system statement to indicate that the default value is not constant (determined by the system)**

See which we agreed is of low-importance. When the default is determined by the system (conditions usually mentioned in the description), it'd be handy for tooling to know that such is the case.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, backcompat-high, importance-med
- **Status**: open
- **Portal**: [Issue 147](https://yang2.wilton.org.uk/issues/147)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 148](<https://github.com/netmod-wg/yang-next/issues/148>)  {#issue-148}
{:numbered="false"}
**YANG++**

YANG++ YANG++ is a data modeling language under development. Project Goals Complete the design and specification of the YANG++ language Design and implement open-source plugins - Native compiler support for YANG++ - YANG 1.1 Translation Tool Documentation YANG++ DRAFT.

<!-- portal:metadata:start -->

- **Github Labels**: importance-high
- **Status**: open
- **Portal**: [Issue 148](https://yang2.wilton.org.uk/issues/148)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 149](<https://github.com/netmod-wg/yang-next/issues/149>)  {#issue-149}
{:numbered="false"}
**Error statement for actions rpcs**

In some cases for rpcs and actions there is a need to supply detailed error information beyond what is specified in RFC 6241. NETCONF allows for additional information in the "error-message" or "error-info" elements, however there is no way to define the structure and syntax of the error message.

<!-- portal:metadata:start -->

- **Github Labels**: complexity-low, importance-high
- **Status**: open
- **Portal**: [Issue 149](https://yang2.wilton.org.uk/issues/149)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Larger improvement
- **Rationale**: Initial classification from the draft Larger improvements table.

<!-- portal:metadata:end -->

## [Issue 150](<https://github.com/netmod-wg/yang-next/issues/150>)  {#issue-150}
{:numbered="false"}
**nbc-change-stmt**

There is a need for a new statement to identify an NBC change in an exportable statement. The Semver extension is not helpful since it is at the module-level instead of the object level, This cannot be done with the 'deprecated' statement in #130 since the NBC change is most likely done in a 'current' definition with no intent to deprecate and replace it.

<!-- portal:metadata:start -->

- **Github Labels**: none
- **Status**: open
- **Portal**: [Issue 150](https://yang2.wilton.org.uk/issues/150)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 151](<https://github.com/netmod-wg/yang-next/issues/151>)  {#issue-151}
{:numbered="false"}
**YANG profiles and views**

There a trend (especially in IETF WGs) to create new YANG models to address some specific use cases rather than re-using existing YANG models because existing YANG models either provides more information than needed for that specific application or the information provided is not formatted as expected by the application Slides presented and discussed during the IETF 121 side meeting: YANG Profiles and Views v00.pptx.

<!-- portal:metadata:start -->

- **Github Labels**: none
- **Status**: open
- **Portal**: [Issue 151](https://yang2.wilton.org.uk/issues/151)
- **Classification**: Do not pursue
- **Rationale**: Initial classification from the draft section: open issues proposed for closure.

<!-- portal:metadata:end -->

## [Issue 152](<https://github.com/netmod-wg/yang-next/issues/152>)  {#issue-152}
{:numbered="false"}
**YANG-next issue summary**

Proposes a method to summarize and prioritize YANG-next issues by grouping and filtering by importance and complexity.

<!-- portal:metadata:start -->

- **Github Labels**: none
- **Status**: open
- **Portal**: [Issue 152](https://yang2.wilton.org.uk/issues/152)
- **Classification**: Unclassified

<!-- portal:metadata:end -->

## [Issue 153](<https://github.com/netmod-wg/yang-next/issues/153>)  {#issue-153}
{:numbered="false"}
**Allow 'when' to be a child of the 'refine' statement**

Already if-feature and must statements are children under "refine", why not the "when" statement...?

<!-- portal:metadata:start -->

- **Github Labels**: none
- **Status**: open
- **Portal**: [Issue 153](https://yang2.wilton.org.uk/issues/153)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 154](<https://github.com/netmod-wg/yang-next/issues/154>)  {#issue-154}
{:numbered="false"}
**Add ability to remove nodes from a grouping in a "uses" statement**

This issue requests: Add ability to remove nodes from a grouping in a "uses" statement.

<!-- portal:metadata:start -->

- **Github Labels**: none
- **Status**: open
- **Portal**: [Issue 154](https://yang2.wilton.org.uk/issues/154)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Larger improvement
- **Rationale**: Initial classification from the draft Larger improvements table.

<!-- portal:metadata:end -->

## [Issue 155](<https://github.com/netmod-wg/yang-next/issues/155>)  {#issue-155}
{:numbered="false"}
**Allow hexadecimal notation for enum values**

It would be beneficial to allow encoding of the enum value in hexadecimal, example: This is not allowed in YANG 1.1. Howere, default integer values are allowed to be encoded in hexadecimal.

<!-- portal:metadata:start -->

- **Github Labels**: none
- **Status**: open
- **Portal**: [Issue 155](https://yang2.wilton.org.uk/issues/155)
- **Classification**: Include in YANG 2.0
- **Subgroup**: Minor enhancement
- **Rationale**: Initial classification from the draft Minor enhancements table.

<!-- portal:metadata:end -->

## [Issue 156](<https://github.com/netmod-wg/yang-next/issues/156>)  {#issue-156}
{:numbered="false"}
**canonical forms for strings (aka: the mac-address issue)**

mac-address in both IETF and IEEE yang is defined as a string. The canonical representations are not the same.

<!-- portal:metadata:start -->

- **Github Labels**: none
- **Status**: open
- **Portal**: [Issue 156](https://yang2.wilton.org.uk/issues/156)
- **Classification**: Defer beyond YANG 2.0
- **Rationale**: Initial classification from the draft section: possible issues for a future YANG version.

<!-- portal:metadata:end -->

## [Issue 157](<https://github.com/netmod-wg/yang-next/issues/157>)  {#issue-157}
{:numbered="false"}
**Clarify behaviour of validating leafref/instance-identifier in unions**

<!-- portal:description:start -->

Cached GitHub description:

```
When parsing unions containing leafrefs or instance-identifiers make it clear what the behaviour is, particularly with require-instance true.

I.e., when choosing the matching type of the union does it require the leafref/instance-identifier to point to a valid data node, or is it just a syntactic check, which could then subsequently fail because the type could be found.

I think that the check should be for syntax only, but either way we should clarify and be explicit on what the expected behaviour is.
```

<!-- portal:description:end -->

<!-- portal:metadata:start -->

- **Github Labels**: none
- **Status**: open
- **Portal**: [Issue 157](https://yang2.wilton.org.uk/issues/157)
- **Classification**: Unclassified

<!-- portal:metadata:end -->

## [Issue 158](<https://github.com/netmod-wg/yang-next/issues/158>)  {#issue-158}
{:numbered="false"}
**More feature rich imports**

<!-- portal:description:start -->

Cached GitHub description:

```
It would be nice if you can restrict what you import from a module. I would like to see:
 - import only typedefs
 - import only identities
 - import everything except schema tree
 - import only schema tree (and maybe features)
```

<!-- portal:description:end -->

<!-- portal:metadata:start -->

- **Github Labels**: none
- **Status**: open
- **Portal**: [Issue 158](https://yang2.wilton.org.uk/issues/158)
- **Classification**: Unclassified

<!-- portal:metadata:end -->

## [Issue 159](<https://github.com/netmod-wg/yang-next/issues/159>)  {#issue-159}
{:numbered="false"}
**Grouping DAG dependecies**

<!-- portal:description:start -->

Cached GitHub description:

````
I call this situation an ANIMA problem. During the modeling of ANIMA protocols following situation emerges:
```
 +-----+           +-----+
 |  A  | --------> |  B  |
 +-----+           +-----+
     \                 \
      \                 \
       v                 v
    +-----+           +-----+
    |  C  | --------> |  D  |
    +-----+           +-----+
```

But the grouping `D` could not be defined because the grouping `C` shares same schema node definition with grouping `B`. In the time when the grouping `C` is added the to grouping `D` with `uses`, the groping already contains the schema node defs and therefore the process fails.

Maybe we want to fix such usage of grouping in the yang-next.
````

<!-- portal:description:end -->

<!-- portal:metadata:start -->

- **Github Labels**: none
- **Status**: open
- **Portal**: [Issue 159](https://yang2.wilton.org.uk/issues/159)
- **Classification**: Unclassified

<!-- portal:metadata:end -->

## [Issue 160](<https://github.com/netmod-wg/yang-next/issues/160>)  {#issue-160}
{:numbered="false"}
**Type union**

<!-- portal:description:start -->

Cached GitHub description:

```
I would like to see an artificial rule that forbids using same name for `enum` and `bits` inside the `union` type.

Such rule simplifies the implementation of unions parsing greatly.
```

<!-- portal:description:end -->

<!-- portal:metadata:start -->

- **Github Labels**: none
- **Status**: open
- **Portal**: [Issue 160](https://yang2.wilton.org.uk/issues/160)
- **Classification**: Unclassified

<!-- portal:metadata:end -->
