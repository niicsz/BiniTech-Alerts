package com.binitech.alerts.adapters.outbound.persistence

import com.binitech.alerts.application.ports.outbound.AlertRepositoryPort
import com.binitech.alerts.domain.Alert
import java.time.Duration
import java.time.Instant
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query

class MongoAlertRepository(private val mongo: MongoTemplate) : AlertRepositoryPort {

    fun ensureIndexes() {
        val ops = mongo.indexOps(COLLECTION)
        ops.ensureIndex(
            Index().on(FINGERPRINT, Sort.Direction.ASC).on(LAST_SEEN, Sort.Direction.DESC).named("idx_fingerprint_last_seen"),
        )
        ops.ensureIndex(Index().on(LAST_SEEN, Sort.Direction.DESC).named("idx_last_seen"))
        ops.ensureIndex(Index().on(FIRST_SEEN, Sort.Direction.ASC).expire(RETENTION).named("ttl_first_seen"))
    }

    override fun save(alert: Alert): Alert = mongo.save(alert, COLLECTION)

    override fun findById(id: String): Alert? = mongo.findById(id, Alert::class.java, COLLECTION)

    override fun findOpenByFingerprint(fingerprint: String, since: Instant): Alert? {
        val query = Query.query(
            Criteria.where(FINGERPRINT).`is`(fingerprint)
                .and("acknowledgedAt").`is`(null)
                .and(LAST_SEEN).gte(since),
        ).with(Sort.by(Sort.Direction.DESC, LAST_SEEN)).limit(1)
        return mongo.findOne(query, Alert::class.java, COLLECTION)
    }

    override fun recent(limit: Int): List<Alert> =
        mongo.find(Query().with(Sort.by(Sort.Direction.DESC, LAST_SEEN)).limit(limit), Alert::class.java, COLLECTION)

    companion object {
        const val COLLECTION = "alerts"
        private const val FINGERPRINT = "fingerprint"
        private const val LAST_SEEN = "lastSeenAt"
        private const val FIRST_SEEN = "firstSeenAt"
        private val RETENTION: Duration = Duration.ofDays(30)
    }
}
