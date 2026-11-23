package dev.yuyuyuyuyu.djmini.similar

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert

@Entity(tableName = "song_vectors")
internal class SongVectorEntity(
    @PrimaryKey @ColumnInfo(name = "song_id") val songId: Long,
    @ColumnInfo(name = "modified") val modified: Long,
    @ColumnInfo(name = "vector") val vector: ByteArray?,
)

internal data class AnalyzedSong(
    @ColumnInfo(name = "song_id") val songId: Long,
    @ColumnInfo(name = "modified") val modified: Long,
)

@Dao
internal interface SongVectorDao {
    @Query("SELECT song_id, modified FROM song_vectors")
    suspend fun analyzedSongs(): List<AnalyzedSong>

    @Query("SELECT * FROM song_vectors WHERE vector IS NOT NULL")
    suspend fun allWithVectors(): List<SongVectorEntity>

    @Upsert
    suspend fun upsert(vector: SongVectorEntity)

    @Query("DELETE FROM song_vectors WHERE song_id IN (:songIds)")
    suspend fun delete(songIds: List<Long>)
}

@Database(entities = [SongVectorEntity::class], version = 1)
internal abstract class SongVectorDatabase : RoomDatabase() {
    abstract fun songVectors(): SongVectorDao

    companion object {
        @Volatile private var instance: SongVectorDatabase? = null

        fun get(context: Context): SongVectorDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(context.applicationContext, SongVectorDatabase::class.java, "song_vectors.db")
                    .build()
                    .also { instance = it }
            }
    }
}
