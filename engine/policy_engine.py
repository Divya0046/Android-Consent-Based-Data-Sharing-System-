"""
Faithful Python port of the Android PolicyEngine / DataBridge / DataSafetyRepository
logic found in image-preview/src/main/java/com/example/image_preview/.

Why a port instead of running the Android app:
- PolicyEngine.validateRequest() and DataBridge's matching logic are pure,
  deterministic functions once you factor out `Context` (which is only used
  to (a) read policies.json from assets and (b) read/write SharedPreferences).
- Re-implementing the identical algorithm (same normalize(), same
  purposeContains(), same 7-step match sequence) as plain Python lets us run
  thousands of controlled + real-world trials headlessly, without an
  emulator/gradle build, which is the only realistic way to get RQ1-RQ4
  results before the deadline.
- The research plan itself (section 18/22) says foreground-detection and
  Firebase must NOT be dependencies of the core experiment, and that the
  experiment should select the app directly from policies.json — this
  harness does exactly that.

Every function below mirrors its Kotlin counterpart 1:1 (same field order,
same normalization, same short-circuit reason ordering), so results are
directly attributable to the deployed logic.
"""

import json
import re
import time
import random
from dataclasses import dataclass, field
from typing import List, Optional

WS_RE = re.compile(r"\s+")


def normalize(value: str) -> str:
    # Kotlin: value.trim().replace(Regex("\\s+"), " ").lowercase()
    return WS_RE.sub(" ", value.strip()).lower()


def purpose_contains(declared_purposes: str, requested_purpose: str) -> bool:
    # Kotlin purposeContains(): split declared on ',', normalize each, compare
    requested = normalize(requested_purpose)
    return any(normalize(p) == requested for p in declared_purposes.split(","))


@dataclass
class DssSharedData:
    data: str
    category: str
    purpose: str
    optional: bool = False


@dataclass
class Policy:
    appId: str
    thirdParty: str
    data: str
    category: str
    purpose: str
    optional: bool
    allowed: bool = True
    createdAt: int = 0


@dataclass
class DataShareRequest:
    appId: str
    thirdParty: str
    data: str
    category: str
    purpose: str


@dataclass
class ValidationRow:
    field: str
    value: str
    isMatch: bool


@dataclass
class ValidationResult:
    allowed: bool
    reason: str
    latency_ns: int
    rows: List[ValidationRow] = field(default_factory=list)


class DataSafetyRepository:
    """Mirrors DataSafetyRepository.kt, reading the same policies.json."""

    def __init__(self, policies_json_path: str):
        with open(policies_json_path, "r", encoding="utf-8") as f:
            self._apps = json.load(f)
        # Index by lower-cased appId for O(1) lookup (Kotlin version does a
        # linear scan over the JSONArray; we keep an index for speed at
        # scale but the *matching semantics* are identical).
        self._by_app = {}
        for app in self._apps:
            app_id = app.get("appId", "")
            if not app_id:
                continue
            self._by_app.setdefault(app_id.lower(), app)

    def get_shared_data_by_package(self, package_name: str) -> List[DssSharedData]:
        app = self._by_app.get(package_name.lower())
        if not app:
            return []
        app_info = app.get("appInfo") or {}
        shared = app_info.get("sharedData") or []
        result = []
        for item in shared:
            data = (item.get("data") or "").strip()
            category = (item.get("type") or "").strip()
            purpose = (item.get("purpose") or "").strip()
            optional = bool(item.get("optional", False))
            if data and category and purpose:
                result.append(DssSharedData(data, category, purpose, optional))
        return result

    def get_apps_with_shared_data(self) -> List[str]:
        result = []
        for app in self._apps:
            app_id = (app.get("appId") or "").strip()
            if not app_id:
                continue
            app_info = app.get("appInfo") or {}
            shared = app_info.get("sharedData") or []
            if shared:
                result.append(app_id)
        # distinct(), preserve order
        seen = set()
        out = []
        for a in result:
            if a not in seen:
                seen.add(a)
                out.append(a)
        return out


class DataBridge:
    """
    Mirrors bridge/DataBridge.kt. In the Android app this is backed by
    SharedPreferences; here it's an in-memory list, which is what the
    scalability experiment (section 13 of the research plan) needs anyway
    (we control exactly how many policies are "stored").
    """

    def __init__(self):
        self._policies: List[Policy] = []

    def save_policy(self, policy: Policy):
        for i, p in enumerate(self._policies):
            if (
                normalize(p.appId) == normalize(policy.appId)
                and normalize(p.thirdParty) == normalize(policy.thirdParty)
                and normalize(p.data) == normalize(policy.data)
                and normalize(p.category) == normalize(policy.category)
                and normalize(p.purpose) == normalize(policy.purpose)
            ):
                self._policies[i] = policy
                return
        self._policies.append(policy)

    def save_policies(self, policies: List[Policy]):
        for p in policies:
            self.save_policy(p)

    def get_policies(self) -> List[Policy]:
        return self._policies

    def clear_policies(self):
        self._policies = []

    def storage_bytes(self) -> int:
        """Approximates DataBridge's persisted SharedPreferences JSON blob size."""
        as_dicts = [p.__dict__ for p in self._policies]
        return len(json.dumps(as_dicts).encode("utf-8"))


class PolicyEngine:
    """Mirrors PolicyEngine.kt validateRequest() exactly."""

    def __init__(self, repository: DataSafetyRepository, bridge: DataBridge):
        self.repository = repository
        self.bridge = bridge

    def validate_request(self, request: DataShareRequest) -> ValidationResult:
        start = time.perf_counter_ns()

        dss_records = self.repository.get_shared_data_by_package(request.appId)
        stored_policies = self.bridge.get_policies()

        matching_consent: Optional[Policy] = None
        for policy in stored_policies:
            if (
                policy.allowed
                and normalize(policy.appId) == normalize(request.appId)
                and normalize(policy.thirdParty) == normalize(request.thirdParty)
                and normalize(policy.data) == normalize(request.data)
                and normalize(policy.category) == normalize(request.category)
                and purpose_contains(policy.purpose, request.purpose)
            ):
                matching_consent = policy
                break

        consent_exists = matching_consent is not None

        matching_dss = None
        for dss in dss_records:
            if normalize(dss.data) == normalize(request.data) and normalize(
                dss.category
            ) == normalize(request.category) and purpose_contains(
                dss.purpose, request.purpose
            ):
                matching_dss = dss
                break

        dss_matches = matching_dss is not None

        app_matches = bool(
            matching_consent and normalize(matching_consent.appId) == normalize(request.appId)
        )
        third_party_matches = bool(
            matching_consent
            and normalize(matching_consent.thirdParty) == normalize(request.thirdParty)
        )
        data_matches = bool(
            matching_consent and normalize(matching_consent.data) == normalize(request.data)
        )
        category_matches = bool(
            matching_consent
            and normalize(matching_consent.category) == normalize(request.category)
        )
        purpose_matches = bool(
            matching_consent and purpose_contains(matching_consent.purpose, request.purpose)
        )

        allowed = (
            consent_exists
            and app_matches
            and third_party_matches
            and data_matches
            and category_matches
            and purpose_matches
            and dss_matches
        )

        if not consent_exists:
            reason = "No matching user consent"
        elif not app_matches:
            reason = "Application mismatch"
        elif not third_party_matches:
            reason = "Third-party mismatch"
        elif not data_matches:
            reason = "Data mismatch"
        elif not category_matches:
            reason = "Category mismatch"
        elif not purpose_matches:
            reason = "Purpose mismatch"
        elif not dss_matches:
            reason = "Data-sharing attributes are not declared in sharedData"
        else:
            reason = "All authorization checks passed"

        latency_ns = time.perf_counter_ns() - start

        rows = [
            ValidationRow("Application", request.appId, app_matches),
            ValidationRow("Third Party", request.thirdParty, third_party_matches),
            ValidationRow("Data", request.data, data_matches and dss_matches),
            ValidationRow("Category", request.category, category_matches and dss_matches),
            ValidationRow("Purpose", request.purpose, purpose_matches and dss_matches),
        ]

        return ValidationResult(allowed=allowed, reason=reason, latency_ns=latency_ns, rows=rows)


# ---------------------------------------------------------------------------
# Baseline (comparison) models, used only for the evaluation section.
# These are NOT in the Android codebase; they are minimal reference
# implementations of the two consent models the paper argues against,
# built for a fair, reproducible empirical comparison since no directly
# equivalent open-source enforcement system exists in the literature
# surveyed (see literature review: all 10 papers are detection/measurement
# studies, not enforcement frameworks).
# ---------------------------------------------------------------------------


class DisclosureOnlyBaseline:
    """
    Baseline A — "Disclosure-only" (status quo Google Play Data Safety).
    Represents today's reality: the Data Safety section is informational
    only. There is no runtime gate, so any request from an app that has
    *any* declared sharedData is implicitly allowed (nothing ever blocks
    at request time); apps with no declared sharedData still are not
    technically prevented from sharing (no enforcement exists at all) but
    for a like-for-like ALLOW/DENY comparison we model its most permissive
    real behavior: no runtime check is performed, i.e. always ALLOW.
    """

    def __init__(self, repository: DataSafetyRepository):
        self.repository = repository

    def validate_request(self, request: DataShareRequest) -> ValidationResult:
        start = time.perf_counter_ns()
        # No enforcement performed at all -> always allowed.
        latency_ns = time.perf_counter_ns() - start
        return ValidationResult(True, "No runtime enforcement (disclosure-only)", latency_ns, [])


class AppLevelCoarseBaseline:
    """
    Baseline B — "App-level coarse consent".
    Mirrors what most existing Android consent/permission-broker style
    controls actually check: has the user consented to *this app* sharing
    data with *this third party* at all? Data type, category and purpose
    are ignored, so once a user allows (App, ThirdParty), every data
    item/category/purpose combination for that pair is allowed.
    """

    def __init__(self, bridge: "CoarseDataBridge"):
        self.bridge = bridge

    def validate_request(self, request: DataShareRequest) -> ValidationResult:
        start = time.perf_counter_ns()
        allowed = self.bridge.has_consent(request.appId, request.thirdParty)
        latency_ns = time.perf_counter_ns() - start
        reason = "App+ThirdParty consent on file" if allowed else "No app-level consent"
        return ValidationResult(allowed, reason, latency_ns, [])


class CoarseDataBridge:
    """Stores only (appId, thirdParty) pairs -> allowed, for Baseline B."""

    def __init__(self):
        self._pairs = set()

    def save(self, app_id: str, third_party: str):
        self._pairs.add((normalize(app_id), normalize(third_party)))

    def has_consent(self, app_id: str, third_party: str) -> bool:
        return (normalize(app_id), normalize(third_party)) in self._pairs
