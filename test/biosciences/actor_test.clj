(ns biosciences.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [biosciences.actor :as actor]
            [biosciences.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-project! st {:project-id "proj-1" :title "Field Ecology Survey"})
    (store/register-specimen! st {:specimen-id "sp-1" :project-id "proj-1" :species "Quercus robur" :location "Forest A" :date "2026-07-14"})
    (store/register-site! st {:site-id "site-1" :project-id "proj-1" :name "North Field" :coordinates "52.5,13.4"})
    (store/register-equipment! st {:equipment-id "eq-1" :name "Field Survey Kit"})
    st))

(deftest commits-a-clean-low-risk-analysis-request
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:project-id "proj-1" :specimen-id "sp-1" :op :analyze-specimen-data :stake :low}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "proj-1"))))))

(deftest holds-on-unregistered-project-without-committing
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:project-id "no-such-project" :op :analyze-specimen-data :stake :low :specimen-id "sp-1"}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "no-such-project")))
    (is (= :hold (:disposition (:state result))))))

(deftest holds-on-missing-specimen-for-analyze-op
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:project-id "proj-1" :specimen-id "no-such-specimen" :op :analyze-specimen-data :stake :low}
        result (actor/run-request! graph request {} "thread-3")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "proj-1")))
    (is (= :hold (:disposition (:state result))))))

(deftest holds-on-missing-site-when-site-id-provided
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:project-id "proj-1" :specimen-id "sp-1" :site-id "no-such-site" :op :analyze-specimen-data :stake :low}
        result (actor/run-request! graph request {} "thread-3b")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "proj-1")))
    (is (= :hold (:disposition (:state result))))))

(deftest interrupts-then-commits-on-human-approval-for-species-anomaly-flag
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        ;; flag-species-anomaly always escalates (governor invariant)
        request {:project-id "proj-1" :op :flag-species-anomaly :stake :high :anomaly-type :invasive-species}
        interrupted (actor/run-request! graph request {} "thread-4")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "proj-1")))
    (let [resumed (actor/approve! graph "thread-4")]
      (is (= :done (:status resumed)))
      (is (some? (get-in resumed [:state :record])))
      (is (= 1 (count (store/records-of st "proj-1")))))))

(deftest holds-on-finalized-report-claim-in-draft
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        ;; report proposals claiming finalization are hard-rejected
        request {:project-id "proj-1" :op :draft-report :stake :high :finalized? true}
        result (actor/run-request! graph request {} "thread-5")]
    (is (= :done (:status result)))
    (is (nil? (get-in result [:state :record])))
    (is (empty? (store/records-of st "proj-1")))
    (is (= :hold (:disposition (:state result))))))

(deftest interrupts-on-significant-finding-in-draft-report
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        ;; significant findings trigger escalation
        request {:project-id "proj-1" :op :draft-report :stake :medium :significant-finding? true}
        interrupted (actor/run-request! graph request {} "thread-6")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "proj-1")))
    (let [resumed (actor/approve! graph "thread-6")]
      (is (= :done (:status resumed)))
      (is (some? (get-in resumed [:state :record])))
      (is (= 1 (count (store/records-of st "proj-1")))))))
