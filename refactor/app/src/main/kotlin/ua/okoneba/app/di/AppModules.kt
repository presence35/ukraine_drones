package ua.okoneba.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ua.okoneba.core.database.OkoNebaDatabase
import ua.okoneba.core.database.dao.AuditLogDao
import ua.okoneba.core.database.dao.EpisodeLedgerDao
import ua.okoneba.core.database.repository.RoomAuditLogRepository
import ua.okoneba.core.database.repository.RoomEpisodeLedgerRepository
import ua.okoneba.core.datastore.DeviceProtectedDataStoreRepository
import ua.okoneba.core.domain.engine.MasterThreatEvaluator
import ua.okoneba.core.domain.engine.ZoneEvaluationEngine
import ua.okoneba.core.domain.repository.AuditLogRepository
import ua.okoneba.core.domain.repository.EpisodeLedgerRepository
import ua.okoneba.core.domain.repository.MonitoringPreferencesRepository
import ua.okoneba.core.network.feed.BackupFeedProvider
import ua.okoneba.core.network.feed.FeedProvider
import ua.okoneba.core.network.feed.NeptunFeedProvider
import ua.okoneba.feature.alerts.location.LocationSanityChecker
import ua.okoneba.feature.alerts.notification.AlertNotificationDispatcher
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeviceProtectedContext

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    @DeviceProtectedContext
    fun provideDeviceProtectedContext(@ApplicationContext context: Context): Context {
        return if (context.isDeviceProtectedStorage) context else context.createDeviceProtectedStorageContext()
    }

    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(@DefaultDispatcher dispatcher: CoroutineDispatcher): CoroutineScope {
        return CoroutineScope(SupervisorJob() + dispatcher)
    }

    @Provides
    @Singleton
    fun provideMasterThreatEvaluator(): MasterThreatEvaluator = MasterThreatEvaluator()

    @Provides
    @Singleton
    fun provideZoneEvaluationEngine(): ZoneEvaluationEngine = ZoneEvaluationEngine()

    @Provides
    @Singleton
    fun provideLocationSanityChecker(): LocationSanityChecker = LocationSanityChecker()

    @Provides
    @Singleton
    fun provideAlertNotificationDispatcher(@ApplicationContext context: Context): AlertNotificationDispatcher {
        return AlertNotificationDispatcher(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideOkoNebaDatabase(@DeviceProtectedContext deContext: Context): OkoNebaDatabase {
        return OkoNebaDatabase.createDeviceProtectedDatabase(deContext)
    }

    @Provides
    fun provideEpisodeLedgerDao(db: OkoNebaDatabase): EpisodeLedgerDao = db.episodeLedgerDao()

    @Provides
    fun provideAuditLogDao(db: OkoNebaDatabase): AuditLogDao = db.auditLogDao()

    @Provides
    @Singleton
    fun provideEpisodeLedgerRepository(
        dao: EpisodeLedgerDao,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): EpisodeLedgerRepository = RoomEpisodeLedgerRepository(dao, ioDispatcher)

    @Provides
    @Singleton
    fun provideAuditLogRepository(
        dao: AuditLogDao,
        @ApplicationScope scope: CoroutineScope,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): AuditLogRepository = RoomAuditLogRepository(dao, scope, ioDispatcher)
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideMonitoringPreferencesRepository(
        @DeviceProtectedContext deContext: Context
    ): MonitoringPreferencesRepository {
        return DeviceProtectedDataStoreRepository.create(deContext)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideNeptunFeedProvider(): NeptunFeedProvider = NeptunFeedProvider()

    @Provides
    @Singleton
    fun provideBackupFeedProvider(): BackupFeedProvider = BackupFeedProvider()

    @Provides
    @Singleton
    fun provideFeedProviders(
        neptun: NeptunFeedProvider,
        backup: BackupFeedProvider
    ): List<FeedProvider> = listOf(neptun, backup)
}
