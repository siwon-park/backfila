package app.cash.backfila.service.persistence

import app.cash.backfila.protos.clientservice.KeyRange
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table
import javax.persistence.Version
import misk.hibernate.DbTimestampedEntity
import misk.hibernate.DbUnsharded
import misk.hibernate.Id
import okio.ByteString

/**
 * Backfill runs can have many partitions, e.g. one per database shard.
 * Each partition tracks cursors individually. They are also leased individually.
 * All partitions of a run have the same running or paused state.
 */
@Entity
@Table(name = "run_partitions")
class DbRunPartition() : DbUnsharded<DbRunPartition>, DbTimestampedEntity {
  @javax.persistence.Id
  @GeneratedValue
  override lateinit var id: Id<DbRunPartition>

  @Column(nullable = false)
  lateinit var backfill_run_id: Id<DbBackfillRun>

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "backfill_run_id", updatable = false, insertable = false)
  lateinit var backfill_run: DbBackfillRun

  @Column(nullable = false)
  lateinit var partition_name: String

  @Column
  override lateinit var created_at: Instant

  @Column
  override lateinit var updated_at: Instant

  @Column(nullable = false)
  @Version
  var version: Long = 0

  /**
   * State of the backfill run, kept in sync with backfill_runs to allow indexing
   * on this and the lease column.
   */
  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  lateinit var run_state: BackfillPartitionState

  @Column
  var lease_token: String? = null

  @Column(nullable = false)
  lateinit var lease_expires_at: Instant

  /**
   * The primary key values only make sense in the context of the client service.
   * We provide them to the client service when needed. We store them transparently
   * as byte strings because they can be any type.
   */
  @Column
  var pkey_cursor: ByteString? = null

  @Column
  var pkey_range_start: ByteString? = null

  @Column
  var pkey_range_end: ByteString? = null

  @Column
  var estimated_record_count: Long? = null

  /**
   * Cursor used to precompute the size of the data.
   * Precomputing is done when this equals pkey_range_end.
   **/
  @Column
  var precomputing_pkey_cursor: ByteString? = null

  @Column(nullable = false)
  var precomputing_done: Boolean = false

  /**
   * How many records in the data set.
   * Not correct until precomputing is done.
   */
  @Column
  var computed_scanned_record_count: Long = 0

  /**
   * How many records in the data set that match the criteria and will be backfilled.
   * Not correct until precomputing is done.
   */
  @Column
  var computed_matching_record_count: Long = 0

  @Column
  var backfilled_scanned_record_count: Long = 0

  @Column
  var backfilled_matching_record_count: Long = 0

  @Column
  var scanned_records_per_minute: Long? = null

  @Column
  var matching_records_per_minute: Long? = null

  constructor(
    backfill_run_id: Id<DbBackfillRun>,
    partition_name: String,
    backfill_range: KeyRange,
    run_state: BackfillPartitionState,
    estimated_record_count: Long?
  ) : this() {
    this.backfill_run_id = backfill_run_id
    this.partition_name = partition_name
    this.pkey_range_start = backfill_range.start
    this.pkey_range_end = backfill_range.end
    this.run_state = run_state
    this.lease_expires_at = Instant.ofEpochSecond(1L)
    this.estimated_record_count = estimated_record_count
  }

  fun backfillRange() = KeyRange(pkey_range_start, pkey_range_end)

  fun clearLease() {
    this.lease_token = null
    this.lease_expires_at = Instant.ofEpochSecond(1L)
  }
}
