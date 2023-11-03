package kr.com.chappiet.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kr.com.chappiet.data.local.LocalDataSource
import kr.com.chappiet.data.model.Summary
import kr.com.chappiet.data.model.DeviceProfile
import javax.inject.Inject

class LocalRepository @OptIn(ExperimentalCoroutinesApi::class)
@Inject constructor(
    private val localDataSource: LocalDataSource
): SummaryRepository, DeviceProfileRepository {
    override suspend fun getAllSummary(): List<Summary> {
        TODO("Not yet implemented")
    }

    override suspend fun saveSummary(summary: Summary) {
        TODO("Not yet implemented")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getProfile(): Flow<DeviceProfile?> = flow {
        emit(localDataSource.getDeviceProfile())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun saveDeviceProfile(profile: DeviceProfile) {
        localDataSource.saveUserProfile(profile)
    }
}