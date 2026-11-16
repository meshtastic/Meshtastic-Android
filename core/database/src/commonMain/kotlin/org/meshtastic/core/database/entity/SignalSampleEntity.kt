/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import org.meshtastic.core.model.SignalSample
import org.meshtastic.core.model.SignalSurveySession

@Entity(tableName = "signal_survey_session")
data class SignalSurveySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    @ColumnInfo(name = "modem_preset") val modemPreset: String? = null,
)

/** A session with the number of readings it holds. */
data class SignalSurveySessionRow(
    @Embedded val session: SignalSurveySessionEntity,
    @ColumnInfo(name = "reading_count") val readingCount: Int,
)

/** One signal survey reading. Holds the phone's own position, so it stays on the device unless the user exports it. */
@Entity(
    tableName = "signal_sample",
    foreignKeys =
    [
        ForeignKey(
            entity = SignalSurveySessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["session_id"])],
)
data class SignalSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "time") val time: Long,
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "accuracy_m") val accuracyM: Float? = null,
    @ColumnInfo(name = "from_num") val fromNum: Int,
    @ColumnInfo(name = "rssi") val rssi: Int,
    @ColumnInfo(name = "snr") val snr: Float? = null,
    @ColumnInfo(name = "direct") val direct: Boolean,
)

fun SignalSample.asEntity(sessionId: Long) = SignalSampleEntity(
    sessionId = sessionId,
    time = timeMillis,
    latitude = latitude,
    longitude = longitude,
    accuracyM = accuracyM,
    fromNum = fromNum,
    rssi = rssi,
    snr = snr,
    direct = direct,
)

fun SignalSampleEntity.asExternalModel() = SignalSample(
    timeMillis = time,
    latitude = latitude,
    longitude = longitude,
    accuracyM = accuracyM,
    fromNum = fromNum,
    rssi = rssi,
    snr = snr,
    direct = direct,
)

fun SignalSurveySessionRow.asExternalModel() = SignalSurveySession(
    id = session.id,
    startedAtMillis = session.startedAt,
    endedAtMillis = session.endedAt,
    modemPreset = session.modemPreset,
    readingCount = readingCount,
)
