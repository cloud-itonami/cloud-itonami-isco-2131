(ns biosciences.store
  "SSoT for the ISCO-08 2131 research support actor (biologists).
  Store is a protocol injected into the `biosciences.actor`
  StateGraph — `MemStore` is the default, deterministic, zero-dep
  backend; a Datomic/kotoba-server-backed implementation can be
  swapped in without touching the actor or governor (itonami actor
  pattern, per ADR-2607011000 / CLAUDE.md Actors section).

  Domain:

    project  — a registered research project (:project-id, :title)
    specimen — a recorded biological specimen from field or lab
               (:specimen-id, :project-id, :species, :location, :date)
    site     — a field survey location (:site-id, :project-id, :name, :coordinates)
    equipment — a field research equipment resource (:equipment-id, :name)
    record   — a committed field/lab operation under a project
               (specimen analysis, report draft, species anomaly flag,
               equipment time request) — written ONLY via commit-record!,
               never mutated in place
    ledger   — an append-only audit trail of every proposal/verdict/
               disposition, regardless of outcome (commit or hold)")

(defprotocol Store
  (project [s project-id])
  (specimen [s specimen-id])
  (site [s site-id])
  (equipment [s equipment-id])
  (records-of [s project-id])
  (ledger [s])
  (register-project! [s project])
  (register-specimen! [s specimen])
  (register-site! [s site])
  (register-equipment! [s equipment])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (project [_ project-id] (get-in @a [:projects project-id]))
  (specimen [_ specimen-id] (get-in @a [:specimens specimen-id]))
  (site [_ site-id] (get-in @a [:sites site-id]))
  (equipment [_ equipment-id] (get-in @a [:equipment equipment-id]))
  (records-of [_ project-id] (filter #(= project-id (:project-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-project! [s project]
    (swap! a assoc-in [:projects (:project-id project)] project) s)
  (register-specimen! [s specimen]
    (swap! a assoc-in [:specimens (:specimen-id specimen)] specimen) s)
  (register-site! [s site]
    (swap! a assoc-in [:sites (:site-id site)] site) s)
  (register-equipment! [s equipment]
    (swap! a assoc-in [:equipment (:equipment-id equipment)] equipment) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:projects {} :specimens {} :sites {} :equipment {} :records [] :ledger []} seed)))))
