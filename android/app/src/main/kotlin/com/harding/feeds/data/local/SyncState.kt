package com.harding.feeds.data.local

/**
 * Local sync lifecycle of a row.
 *
 * Create, update and delete are distinct states (not a single PENDING) because the contract
 * pushes them with different verbs and semantics: a POST replay for an id the server already
 * has returns the existing feed *unchanged* (idempotent create), so an offline edit pushed as
 * a POST would be silently dropped - it must go as a PUT. A delete must survive locally as a
 * tombstone until the DELETE call succeeds, because the server hard-deletes with no tombstone
 * of its own.
 */
enum class SyncState {
    /** Created locally, server has never seen this id. Pushed as POST /feeds. */
    PENDING_CREATE,

    /** Exists on the server but has local edits. Pushed as PUT /feeds/{id}. */
    PENDING_UPDATE,

    /** Deleted locally, hidden from queries, awaiting DELETE /feeds/{id}. */
    PENDING_DELETE,

    /** Local row matches the server. Safe to overwrite from a pull. */
    SYNCED,
}
