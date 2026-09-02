package ua.okoneba.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ua.okoneba.core.database.dao.AuditLogDao
import ua.okoneba.core.database.dao.EpisodeLedgerDao
import ua.okoneba.core.database.entity.AuditLogEntity
import ua.okoneba.core.database.entity.EpisodeLedgerEntity

@Database(
    entities = [
        EpisodeLedgerEntity::class,
        AuditLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class OkoNebaDatabase : RoomDatabase() {

    abstract fun episodeLedgerDao(): EpisodeLedgerDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        private const val DB_NAME = "okoneba_core_de.db"

        /**
         * Creates a Room database instance using Device-Protected Storage (DE).
         * This makes the database accessible before user unlock (Direct Boot mode).
         */
        fun createDeviceProtectedDatabase(context: Context): OkoNebaDatabase {
            val deContext = if (context.isDeviceProtectedStorage) {
                context
            } else {
                context.createDeviceProtectedStorageContext()
            }

            return Room.databaseBuilder(
                deContext,
                OkoNebaDatabase::class.java,
                DB_NAME
            )
            .fallbackToDestructiveMigration()
            .build()
        }
    }
}
