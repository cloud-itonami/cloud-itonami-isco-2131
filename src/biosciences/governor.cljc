(ns biosciences.governor
  "BiologyGovernor — the independent safety/traceability layer for
  the ISCO-08 2131 research support actor (biologists). Wired as its own
  `:govern` node in `biosciences.actor`'s StateGraph, downstream of
  `:advise` — the Advisor has no notion of project provenance or field
  safety risk, so this MUST be a separate system able to reject a
  proposal (itonami actor pattern, per ADR-2607011000 / CLAUDE.md
  Actors section).

  `check` is a pure function of (request, context, proposal, store) ->
  verdict; it never mutates the store. The StateGraph's `:decide` node
  routes on the verdict:
    :hard? true                → :hold  (irreversible, no write)
    :escalate? true            → :request-approval (interrupt-before)
    otherwise                  → :commit

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. project provenance  — the request's project must be registered.
    2. specimen verification — :analyze-specimen-data ops must reference a
                              registered specimen.
    3. site verification — :analyze-specimen-data ops at a site must
                          reference a registered site.
    4. no-actuation — proposal :effect must be :propose.
    5. no-finalized-claims — :draft-report proposals can never claim
                             a result as finalized/publishable
                             (draft is draft-for-review only, not the
                             final-report proposal).

  ESCALATION invariants (:escalate? true, ALWAYS human sign-off):
    6. :flag-species-anomaly — always escalates (conservation/ecosystem
                              safeguard, never silently dismissed).
    7. :draft-report with :significant-finding? true — significant
                                                     ecological findings
                                                     require human review
                                                     before proceeding.
    8. low confidence (< `confidence-floor`)."
  (:require [biosciences.store :as store]))

(def confidence-floor 0.6)
(def ^:private escalating-ops #{:flag-species-anomaly :draft-report})

(defn- hard-violations [{:keys [proposal request]} project-record specimen-record site-record]
  (cond-> []
    (nil? project-record)
    (conj {:rule :no-project :detail "未登録 project"})

    (and (= :analyze-specimen-data (:op proposal))
         (nil? specimen-record))
    (conj {:rule :no-specimen :detail "analyze-specimen-data 前に specimen は要登録"})

    (and (= :analyze-specimen-data (:op proposal))
         (:site-id request)
         (nil? site-record))
    (conj {:rule :no-site :detail "site reference がある場合、site は要登録"})

    (not= :propose (:effect proposal))
    (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

    (and (= :draft-report (:op proposal))
         (:finalized? proposal))
    (conj {:rule :no-finalized-claims :detail "report 最終化は draft 提案では不可（draft は査読用のみ）"})))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `biosciences.store/Store`. Returns
  `{:ok? bool :violations [...] :confidence n :hard? bool :escalate? bool}`."
  [request context proposal store]
  (let [project-record (store/project store (:project-id request))
        specimen-record (when (:specimen-id request) (store/specimen store (:specimen-id request)))
        site-record (when (:site-id request) (store/site store (:site-id request)))
        hard (hard-violations {:proposal proposal :request request} project-record specimen-record site-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        is-flag? (= :flag-species-anomaly (:op proposal))
        is-draft-significant? (and (= :draft-report (:op proposal)) (:significant-finding? proposal))
        risky-op? (and (contains? escalating-ops (:op proposal))
                       (or is-flag? is-draft-significant?))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))
