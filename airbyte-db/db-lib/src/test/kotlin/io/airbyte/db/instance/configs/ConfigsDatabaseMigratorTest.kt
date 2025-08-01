/*
 * Copyright (c) 2020-2025 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.db.instance.configs

import io.airbyte.db.factory.FlywayFactory.create
import io.airbyte.db.instance.DatabaseMigrator
import io.airbyte.db.instance.development.MigrationDevHelper.dumpSchema
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

internal class ConfigsDatabaseMigratorTest : AbstractConfigsDatabaseTest() {
  @Test
  @Throws(IOException::class)
  fun dumpSchema() {
    val schemaDumpFile = File.createTempFile("configs-schema-dump", "txt")
    schemaDumpFile.deleteOnExit()

    val flyway =
      create(
        dataSource!!,
        javaClass.simpleName,
        ConfigsDatabaseMigrator.DB_IDENTIFIER,
        ConfigsDatabaseMigrator.MIGRATION_FILE_LOCATION,
      )

    val migrator: DatabaseMigrator = ConfigsDatabaseMigrator(database!!, flyway)
    migrator.migrate()

    val schema = migrator.dumpSchema()
    dumpSchema(schema, schemaDumpFile.absolutePath, false)

    val dumpedSchema = Files.readString(schemaDumpFile.toPath())

    Assertions.assertTrue(schemaDumpFile.exists())
    Assertions.assertEquals(schema.trim(), dumpedSchema.trim())
  }
}
