package com.geeksville.mesh.util

import android.database.Cursor
import org.osmdroid.tileprovider.modules.DatabaseFileArchive
import org.osmdroid.tileprovider.modules.SqlTileWriter


/**
 * Extended the sqlite tile writer to have some additional query functions. A this point
 * it's unclear if there is a need to put these with the osmdroid-android library, thus they were
 * put here as more of an example.
 *
 *
 * created on 12/21/2016.
 *
 * @author Alex O'Ree
 * @since 5.6.2
 */
class SqlTileWriterExt() : SqlTileWriter() {
    fun select(rows: Int, offset: Int): Cursor? {
        return this.db?.rawQuery(
            "select " + DatabaseFileArchive.COLUMN_KEY + "," + COLUMN_EXPIRES + "," + DatabaseFileArchive.COLUMN_PROVIDER + " from " + DatabaseFileArchive.TABLE + " limit ? offset ?",
            arrayOf(rows.toString() + "", offset.toString() + "")
        )
    }

    /**
     * gets all the tiles sources that we have tiles for in the cache database and their counts
     *
     * @return
     */
    val sources: List<SourceCount>
        get() {
            val db = db
            val ret: MutableList<SourceCount> = ArrayList()
            if (db == null) {
                return ret
            }
            var cur: Cursor? = null
            try {
                cur = db.rawQuery(
                    "select "
                            + DatabaseFileArchive.COLUMN_PROVIDER
                            + ",count(*) "
                            + ",min(length(" + DatabaseFileArchive.COLUMN_TILE + ")) "
                            + ",max(length(" + DatabaseFileArchive.COLUMN_TILE + ")) "
                            + ",sum(length(" + DatabaseFileArchive.COLUMN_TILE + ")) "
                            + "from " + DatabaseFileArchive.TABLE + " "
                            + "group by " + DatabaseFileArchive.COLUMN_PROVIDER, null
                )
                while (cur.moveToNext()) {
                    val c = SourceCount()
                    c.source = cur.getString(0)
                    c.rowCount = cur.getLong(1)
                    c.sizeMin = cur.getLong(2)
                    c.sizeMax = cur.getLong(3)
                    c.sizeTotal = cur.getLong(4)
                    c.sizeAvg = c.sizeTotal / c.rowCount
                    ret.add(c)
                }
            } catch (e: Exception) {
                catchException(e)
            } finally {
                cur?.close()
            }
            return ret
        }
    val rowCountExpired: Long
        get() = getRowCount(
            "$COLUMN_EXPIRES<?", arrayOf(System.currentTimeMillis().toString())
        )

    class SourceCount() {
        var rowCount: Long = 0
        var source: String? = null
        var sizeTotal: Long = 0
        var sizeMin: Long = 0
        var sizeMax: Long = 0
        var sizeAvg: Long = 0
    }
}