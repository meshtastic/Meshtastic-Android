/*
 * Copyright (c) 2025 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.geeksville.mesh.network.retrofit

import com.geeksville.mesh.network.model.NetworkDeviceHardware
import com.geeksville.mesh.network.model.NetworkFirmwareReleases
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

private const val ERROR_NO_OP = 420
@Singleton
class NoOpApiService@Inject constructor() : ApiService {
    override suspend fun getDeviceHardware(): Response<List<NetworkDeviceHardware>> {
        return Response.error(ERROR_NO_OP, "Not Found".toResponseBody(null))
    }

    override suspend fun getFirmwareReleases(): Response<NetworkFirmwareReleases> {
        return Response.error(ERROR_NO_OP, "Not Found".toResponseBody(null))
    }
}
