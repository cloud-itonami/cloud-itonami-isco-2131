(ns biosciences.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [biosciences.governor :as governor]
            [biosciences.store :as store]))

(deftest accepts-low-confidence-proposal-with-escalation
  (let [st (store/mem-store)
        _ (store/register-project! st {:project-id "proj-1" :title "Test"})
        _ (store/register-specimen! st {:specimen-id "sp-1" :project-id "proj-1"})
        request {:project-id "proj-1" :specimen-id "sp-1" :op :analyze-specimen-data}
        proposal {:op :analyze-specimen-data :effect :propose :confidence 0.5 :stake :low}
        result (governor/check request {} proposal st)]
    (is (false? (:ok? result)))
    (is (true? (:escalate? result)))
    (is (empty? (:violations result)))))

(deftest rejects-missing-project-as-hard-violation
  (let [st (store/mem-store)
        request {:project-id "no-proj" :op :analyze-specimen-data}
        proposal {:op :analyze-specimen-data :effect :propose :confidence 0.9 :stake :low}
        result (governor/check request {} proposal st)]
    (is (false? (:ok? result)))
    (is (true? (:hard? result)))
    (is (some #(= :no-project (:rule %)) (:violations result)))))

(deftest rejects-missing-specimen-for-analyze-op
  (let [st (store/mem-store)
        _ (store/register-project! st {:project-id "proj-1" :title "Test"})
        request {:project-id "proj-1" :specimen-id "no-sp" :op :analyze-specimen-data}
        proposal {:op :analyze-specimen-data :effect :propose :confidence 0.9}
        result (governor/check request {} proposal st)]
    (is (false? (:ok? result)))
    (is (true? (:hard? result)))
    (is (some #(= :no-specimen (:rule %)) (:violations result)))))

(deftest rejects-missing-site-when-site-id-provided
  (let [st (store/mem-store)
        _ (store/register-project! st {:project-id "proj-1" :title "Test"})
        _ (store/register-specimen! st {:specimen-id "sp-1" :project-id "proj-1"})
        request {:project-id "proj-1" :specimen-id "sp-1" :site-id "no-site" :op :analyze-specimen-data}
        proposal {:op :analyze-specimen-data :effect :propose :confidence 0.9}
        result (governor/check request {} proposal st)]
    (is (false? (:ok? result)))
    (is (true? (:hard? result)))
    (is (some #(= :no-site (:rule %)) (:violations result)))))

(deftest rejects-non-propose-effect-as-hard-violation
  (let [st (store/mem-store)
        _ (store/register-project! st {:project-id "proj-1" :title "Test"})
        request {:project-id "proj-1" :op :analyze-specimen-data}
        proposal {:op :analyze-specimen-data :effect :commit :confidence 0.9}
        result (governor/check request {} proposal st)]
    (is (false? (:ok? result)))
    (is (true? (:hard? result)))
    (is (some #(= :no-actuation (:rule %)) (:violations result)))))

(deftest rejects-finalized-report-as-hard-violation
  (let [st (store/mem-store)
        _ (store/register-project! st {:project-id "proj-1" :title "Test"})
        request {:project-id "proj-1" :op :draft-report}
        proposal {:op :draft-report :effect :propose :confidence 0.9 :finalized? true}
        result (governor/check request {} proposal st)]
    (is (false? (:ok? result)))
    (is (true? (:hard? result)))
    (is (some #(= :no-finalized-claims (:rule %)) (:violations result)))))

(deftest escalates-on-species-anomaly-flag
  (let [st (store/mem-store)
        _ (store/register-project! st {:project-id "proj-1" :title "Test"})
        request {:project-id "proj-1" :op :flag-species-anomaly}
        proposal {:op :flag-species-anomaly :effect :propose :confidence 0.9 :anomaly-type :invasive-species}
        result (governor/check request {} proposal st)]
    (is (false? (:ok? result)))
    (is (true? (:escalate? result)))
    (is (empty? (:violations result)))))

(deftest escalates-on-significant-finding-in-report
  (let [st (store/mem-store)
        _ (store/register-project! st {:project-id "proj-1" :title "Test"})
        request {:project-id "proj-1" :op :draft-report}
        proposal {:op :draft-report :effect :propose :confidence 0.8 :significant-finding? true}
        result (governor/check request {} proposal st)]
    (is (false? (:ok? result)))
    (is (true? (:escalate? result)))
    (is (empty? (:violations result)))))

(deftest accepts-clean-specimen-analysis-with-high-confidence
  (let [st (store/mem-store)
        _ (store/register-project! st {:project-id "proj-1" :title "Test"})
        _ (store/register-specimen! st {:specimen-id "sp-1" :project-id "proj-1"})
        request {:project-id "proj-1" :specimen-id "sp-1" :op :analyze-specimen-data}
        proposal {:op :analyze-specimen-data :effect :propose :confidence 0.95 :stake :low}
        result (governor/check request {} proposal st)]
    (is (true? (:ok? result)))
    (is (false? (:hard? result)))
    (is (false? (:escalate? result)))))
