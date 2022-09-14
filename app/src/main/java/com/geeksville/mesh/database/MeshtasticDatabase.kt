package com.geeksville.mesh.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.geeksville.mesh.database.dao.MeshLogDao
import com.geeksville.mesh.database.dao.QuickChatActionDao
import com.geeksville.mesh.database.entity.MeshLog
import com.geeksville.mesh.database.entity.QuickChatAction

@Database(entities = [MeshLog::class, QuickChatAction::class], version = 3, exportSchema = false)
abstract class MeshtasticDatabase : RoomDatabase() {
    abstract fun meshLogDao(): MeshLogDao
    abstract fun quickChatActionDao(): QuickChatActionDao

    companion object {
        fun getDatabase(context: Context): MeshtasticDatabase {

            return Room.databaseBuilder(
                context.applicationContext,
                MeshtasticDatabase::class.java,
                "meshtastic_database"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}