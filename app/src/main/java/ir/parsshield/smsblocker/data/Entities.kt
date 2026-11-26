package ir.parsshield.smsblocker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import ir.parsshield.smsblocker.engine.CustomRule
import ir.parsshield.smsblocker.engine.RuleAction
import ir.parsshield.smsblocker.engine.RuleTarget
import ir.parsshield.smsblocker.engine.SmsCategory

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["threadAddress"]),
        Index(value = ["isBlocked"]),
        Index(value = ["systemSmsId"], unique = true)
    ]
)
data class SmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val threadAddress: String,
    val sender: String,
    val displayName: String? = null,
    val body: String,
    val timestamp: Long,
    val category: SmsCategory,
    val isBlocked: Boolean,
    val isOutgoing: Boolean = false,
    val otpCode: String? = null,
    val spamScore: Int = 0,
    val reasonSummary: String = "",
    val isRead: Boolean = false,
    val systemSmsId: Long? = null
)

@Entity(tableName = "custom_rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val pattern: String,
    val target: RuleTarget,
    val action: RuleAction,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): CustomRule = CustomRule(
        id = id,
        pattern = pattern,
        target = target,
        action = action
    )
}

class RoomConverters {
    @TypeConverter
    fun fromCategory(value: SmsCategory): String = value.name

    @TypeConverter
    fun toCategory(value: String): SmsCategory =
        runCatching { SmsCategory.valueOf(value) }.getOrDefault(SmsCategory.PERSONAL)

    @TypeConverter
    fun fromRuleTarget(value: RuleTarget): String = value.name

    @TypeConverter
    fun toRuleTarget(value: String): RuleTarget =
        runCatching { RuleTarget.valueOf(value) }.getOrDefault(RuleTarget.SENDER)

    @TypeConverter
    fun fromRuleAction(value: RuleAction): String = value.name

    @TypeConverter
    fun toRuleAction(value: String): RuleAction =
        runCatching { RuleAction.valueOf(value) }.getOrDefault(RuleAction.BLOCK)
}
