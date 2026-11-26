package ir.parsshield.smsblocker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun observeAllMessages(): Flow<List<SmsEntity>>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): SmsEntity?

    @Query("SELECT * FROM messages WHERE threadAddress = :threadAddress AND body = :body AND timestamp >= :minTimestamp LIMIT 1")
    suspend fun findRecentDuplicate(threadAddress: String, body: String, minTimestamp: Long): SmsEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE systemSmsId = :systemId)")
    suspend fun existsBySystemId(systemId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(entity: SmsEntity): Long

    @Update
    suspend fun updateMessage(entity: SmsEntity)

    @Query("UPDATE messages SET isRead = 1 WHERE threadAddress = :threadAddress AND isRead = 0")
    suspend fun markThreadRead(threadAddress: String)

    @Query("UPDATE messages SET isBlocked = 1, category = 'SPAM_AD', isRead = 1 WHERE threadAddress = :threadAddress AND category != 'OTP'")
    suspend fun markThreadBlockedExceptOtp(threadAddress: String)

    @Delete
    suspend fun deleteMessage(entity: SmsEntity)

    @Query("DELETE FROM messages WHERE isBlocked = 1")
    suspend fun clearAllBlocked()

    @Query("SELECT * FROM custom_rules ORDER BY createdAt DESC")
    fun observeRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM custom_rules ORDER BY createdAt DESC")
    suspend fun getAllRulesSnapshot(): List<RuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: RuleEntity): Long

    @Delete
    suspend fun deleteRule(rule: RuleEntity)
}
