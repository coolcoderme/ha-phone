package io.github.coolcoderme.haphone

import io.github.coolcoderme.haphone.data.db.HubMigrations
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationTest {
    @Test
    fun v1ToV2KeepsDevicesScenesAutomationsAndAddsTimestamps() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            conn.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS `devices` (
                      `id` TEXT NOT NULL,
                      `kind` TEXT NOT NULL,
                      `name` TEXT NOT NULL,
                      `host` TEXT NOT NULL,
                      `port` INTEGER NOT NULL,
                      `extrasJson` TEXT NOT NULL,
                      `roomId` TEXT,
                      `favorite` INTEGER NOT NULL,
                      PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                st.execute(HubMigrations.V1_ENTITY_TABLE)
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS `rooms` (
                      `id` TEXT NOT NULL,
                      `name` TEXT NOT NULL,
                      PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS `scenes` (
                      `id` TEXT NOT NULL,
                      `name` TEXT NOT NULL,
                      `actionsJson` TEXT NOT NULL,
                      PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS `automations` (
                      `id` TEXT NOT NULL,
                      `name` TEXT NOT NULL,
                      `enabled` INTEGER NOT NULL,
                      `triggerJson` TEXT NOT NULL,
                      `actionsJson` TEXT NOT NULL,
                      `lastFiredAt` INTEGER NOT NULL,
                      PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                st.execute("INSERT INTO devices VALUES('dev1','WLED','Lamp','10.0.2.2',80,'{}','office',1)")
                st.execute("INSERT INTO entities VALUES('light.lamp','dev1','LIGHT','Lamp',1,1,128,'{}')")
                st.execute("INSERT INTO rooms VALUES('office','Office Test')")
                st.execute("INSERT INTO scenes VALUES('evening','Evening','[]')")
                st.execute("INSERT INTO automations VALUES('morning','Morning lights',1,'{}','[]',0)")

                st.execute(HubMigrations.ADD_LAST_CHANGED)
                st.execute(HubMigrations.ADD_LAST_UPDATED)

                st.executeQuery("SELECT COUNT(*) FROM devices").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(1, rs.getInt(1))
                }
                st.executeQuery("SELECT COUNT(*) FROM rooms WHERE id='office'").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(1, rs.getInt(1))
                }
                st.executeQuery("SELECT COUNT(*) FROM scenes").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(1, rs.getInt(1))
                }
                st.executeQuery("SELECT COUNT(*) FROM automations").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(1, rs.getInt(1))
                }
                st.executeQuery("SELECT name, lastChangedAt, lastUpdatedAt FROM entities WHERE id='light.lamp'").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("Lamp", rs.getString("name"))
                    assertEquals(0L, rs.getLong("lastChangedAt"))
                    assertEquals(0L, rs.getLong("lastUpdatedAt"))
                }
            }
        }
    }
}
