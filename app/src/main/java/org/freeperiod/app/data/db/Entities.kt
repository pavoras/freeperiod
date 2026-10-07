package org.freeperiod.app.data.db

import androidx.room.*
import org.freeperiod.engine.CycleUse
import org.freeperiod.engine.FlowLevel
import org.freeperiod.engine.Mood
import org.freeperiod.engine.Pain
import org.freeperiod.engine.Sex
import org.freeperiod.engine.Discharge

@Entity(tableName = "periods", indices = [Index("startEpochDay")])
data class PeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startEpochDay: Long,
    val endEpochDay: Long?,
    val cycleUse: CycleUse = CycleUse.AUTO,
)

@Entity(tableName = "day_logs")
data class DayLogEntity(
    @PrimaryKey val epochDay: Long,
    val flow: FlowLevel? = null,
    val mood: Mood? = null,
    val symptoms: String = "",
    val pain: Pain? = null,
    val sex: Sex? = null,
    val discharge: Discharge? = null,
    val note: String? = null,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val archived: Boolean = false,
)

@Entity(tableName = "day_tags", primaryKeys = ["epochDay", "tagId"],
    foreignKeys = [
        ForeignKey(entity = DayLogEntity::class, parentColumns = ["epochDay"],
            childColumns = ["epochDay"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"],
            childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ], indices = [Index("tagId")])
data class DayTagEntity(val epochDay: Long, val tagId: Long)

@Entity(tableName = "domain_settings")
data class DomainSettingsEntity(
    @PrimaryKey val id: Int = 0,
    val typicalCycleLength: Int? = null,
    val predictionsPaused: Boolean = false,
)

data class DayLogWithTags(
    @Embedded val log: DayLogEntity,
    @Relation(parentColumn = "epochDay", entityColumn = "epochDay") val links: List<DayTagEntity>,
)
