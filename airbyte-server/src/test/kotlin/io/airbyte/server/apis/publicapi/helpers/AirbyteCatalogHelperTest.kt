/*
 * Copyright (c) 2020-2025 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.server.apis.publicapi.helpers

import com.fasterxml.jackson.databind.JsonNode
import io.airbyte.api.model.generated.AirbyteCatalog
import io.airbyte.api.model.generated.AirbyteStream
import io.airbyte.api.model.generated.AirbyteStreamAndConfiguration
import io.airbyte.api.model.generated.AirbyteStreamConfiguration
import io.airbyte.api.model.generated.DestinationSyncMode
import io.airbyte.api.model.generated.SelectedFieldInfo
import io.airbyte.api.model.generated.SyncMode
import io.airbyte.api.problems.model.generated.ProblemMessageData
import io.airbyte.api.problems.throwable.generated.BadRequestProblem
import io.airbyte.commons.json.Jsons
import io.airbyte.publicApi.server.generated.models.AirbyteApiConnectionSchedule
import io.airbyte.publicApi.server.generated.models.ConfiguredStreamMapper
import io.airbyte.publicApi.server.generated.models.ConnectionSyncModeEnum
import io.airbyte.publicApi.server.generated.models.ScheduleTypeEnum
import io.airbyte.publicApi.server.generated.models.StreamConfiguration
import io.airbyte.publicApi.server.generated.models.StreamConfigurations
import io.airbyte.publicApi.server.generated.models.StreamMapperType
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class AirbyteCatalogHelperTest {
  @Test
  internal fun `test that a stream configuration is not empty`() {
    val streamConfigurations: StreamConfigurations = mockk()

    every { streamConfigurations.streams } returns listOf(mockk<StreamConfiguration>())

    assertTrue(AirbyteCatalogHelper.hasStreamConfigurations(streamConfigurations))
  }

  @Test
  internal fun `test that a stream configuration is empty`() {
    val streamConfigurations: StreamConfigurations = mockk()

    every { streamConfigurations.streams } returns listOf()

    assertFalse(AirbyteCatalogHelper.hasStreamConfigurations(streamConfigurations))

    every { streamConfigurations.streams } returns null

    assertFalse(AirbyteCatalogHelper.hasStreamConfigurations(streamConfigurations))

    assertFalse(AirbyteCatalogHelper.hasStreamConfigurations(null))
  }

  @Test
  internal fun `test that a copy of the AirbyteStreamConfiguration is returned when it is updated to full refresh overwrite mode`() {
    val originalStreamConfiguration = createAirbyteStreamConfiguration()

    val updatedStreamConfiguration = AirbyteCatalogHelper.updateConfigDefaultFullRefreshOverwrite(config = originalStreamConfiguration)
    assertFalse(originalStreamConfiguration === updatedStreamConfiguration)
    assertEquals(SyncMode.FULL_REFRESH, updatedStreamConfiguration.syncMode)
    assertEquals(DestinationSyncMode.OVERWRITE, updatedStreamConfiguration.destinationSyncMode)
  }

  @Test
  internal fun `test that a copy of the AirbyteCatalog is returned when all of its streams are updated to full refresh overwrite mode`() {
    val originalAirbyteCatalog = createAirbyteCatalog()
    val updatedAirbyteCatalog = AirbyteCatalogHelper.updateAllStreamsFullRefreshOverwrite(airbyteCatalog = originalAirbyteCatalog)
    assertFalse(originalAirbyteCatalog === updatedAirbyteCatalog)
    updatedAirbyteCatalog.streams.stream().forEach { stream ->
      assertEquals(SyncMode.FULL_REFRESH, stream.config?.syncMode)
      assertEquals(DestinationSyncMode.OVERWRITE, stream.config?.destinationSyncMode)
    }
  }

  @Test
  internal fun `test getting valid streams for a valid catalog`() {
    val referenceCatalog = createAirbyteCatalog()
    val streamConfigurations =
      StreamConfigurations(
        streams =
          listOf(
            StreamConfiguration(name = "name1", namespace = "namespace"), // stream with ns
            StreamConfiguration(name = "name2"), // stream without ns but still valid bc no duplicates
          ),
      )

    val actual =
      AirbyteCatalogHelper.getValidConfiguredStreams(
        referenceCatalog = referenceCatalog,
        streamConfigurations = streamConfigurations,
        validDestinationSyncModes = emptyList(),
      )

    assertEquals(
      listOf(
        "name1" to "namespace",
        "name2" to "namespace",
      ),
      actual.map { it.stream.name to it.stream.namespace },
    )
  }

  @Test
  internal fun `test that a stream with an invalid name is considered to be invalid`() {
    val referenceCatalog = createAirbyteCatalog()
    val streamConfiguration = StreamConfiguration(name = "unknown", namespace = "unknown")
    val streamConfigurations = StreamConfigurations(streams = listOf(streamConfiguration))

    val throwable =
      assertThrows(BadRequestProblem::class.java) {
        AirbyteCatalogHelper.getValidConfiguredStreams(referenceCatalog = referenceCatalog, streamConfigurations = streamConfigurations, emptyList())
      }
    val problemData: ProblemMessageData = throwable.problem.data as ProblemMessageData
    assertEquals(true, problemData.message.contains("No streams found"))
  }

  @Test
  internal fun `test that streamConfigurations with duplicate streams is considered to be invalid`() {
    val referenceCatalog = createAirbyteCatalog()
    val streamConfiguration1 = StreamConfiguration(name = "name1", namespace = "namespace")
    val streamConfiguration2 = StreamConfiguration(name = "name1", namespace = "namespace")
    val streamConfigurations = StreamConfigurations(streams = listOf(streamConfiguration1, streamConfiguration2))

    val throwable =
      assertThrows(BadRequestProblem::class.java) {
        AirbyteCatalogHelper.getValidConfiguredStreams(referenceCatalog = referenceCatalog, streamConfigurations = streamConfigurations, emptyList())
      }
    val problemData: ProblemMessageData = throwable.problem.data as ProblemMessageData
    assertEquals(true, problemData.message.contains("Duplicate stream found"))
  }

  @Test
  internal fun `test that the cron configuration can be validated`() {
    val connectionSchedule =
      AirbyteApiConnectionSchedule(
        scheduleType = ScheduleTypeEnum.CRON,
        cronExpression = "0 15 10 * * ? * UTC",
      )
    assertTrue(AirbyteCatalogHelper.validateCronConfiguration(connectionSchedule = connectionSchedule))

    val connectionSchedule2 =
      AirbyteApiConnectionSchedule(
        scheduleType = ScheduleTypeEnum.MANUAL,
      )
    assertTrue(AirbyteCatalogHelper.validateCronConfiguration(connectionSchedule = connectionSchedule2))
  }

  @Test
  internal fun `test that the cron expression is normalized`() {
    val connectionSchedule =
      AirbyteApiConnectionSchedule(
        scheduleType = ScheduleTypeEnum.CRON,
        cronExpression = "0 15 10 * * ? * UTC",
      )
    assertFalse(AirbyteCatalogHelper.normalizeCronExpression(connectionSchedule)?.cronExpression?.contains("UTC") ?: false)
  }

  @Test
  internal fun `test that the cron configuration with a missing cron expression is invalid`() {
    val connectionSchedule =
      AirbyteApiConnectionSchedule(
        scheduleType = ScheduleTypeEnum.CRON,
      )

    val throwable =
      assertThrows(BadRequestProblem::class.java) {
        AirbyteCatalogHelper.validateCronConfiguration(connectionSchedule = connectionSchedule)
      }
    val problemData: ProblemMessageData = throwable.problem.data as ProblemMessageData
    assertEquals(true, problemData.message.contains("Missing cron expression in the schedule."))
  }

  @Test
  internal fun `test that the cron configuration with an invalid cron expression length is invalid`() {
    val connectionSchedule =
      AirbyteApiConnectionSchedule(
        scheduleType = ScheduleTypeEnum.CRON,
        cronExpression = "0 15 10 * * ? * * * *",
      )

    val throwable =
      assertThrows(BadRequestProblem::class.java) {
        AirbyteCatalogHelper.validateCronConfiguration(connectionSchedule = connectionSchedule)
      }
    val problemData: ProblemMessageData = throwable.problem.data as ProblemMessageData
    assertEquals(true, problemData.message.contains("Cron expression contains 10 parts but we expect one of [6, 7]"))
  }

  @Test
  internal fun `test that the cron configuration with an invalid cron expression is invalid`() {
    val connectionSchedule =
      AirbyteApiConnectionSchedule(
        scheduleType = ScheduleTypeEnum.CRON,
        cronExpression = "not a valid cron expression string",
      )

    val throwable =
      assertThrows(BadRequestProblem::class.java) {
        AirbyteCatalogHelper.validateCronConfiguration(connectionSchedule = connectionSchedule)
      }
    val problemData: ProblemMessageData = throwable.problem.data as ProblemMessageData
    assertEquals(true, problemData.message.contains("Failed to parse cron expression. Invalid chars in expression!"))
  }

  @ParameterizedTest
  @EnumSource(ConnectionSyncModeEnum::class)
  internal fun `test that when a stream configuration is updated, the correct sync modes are set based on the stream configuration`(
    connectionSyncMode: ConnectionSyncModeEnum,
  ) {
    val cursorField = "cursor"
    val primaryKeyColumn = "primary"
    val streamConfiguration =
      StreamConfiguration(
        name = "name1",
        syncMode = connectionSyncMode,
        cursorField = listOf(cursorField),
        primaryKey = listOf(listOf(primaryKeyColumn)),
      )

    val configuredStreams =
      AirbyteCatalogHelper.getValidConfiguredStreams(
        createAirbyteCatalog(jsonSchema = cursorPrimaryKeyJsonSchema()),
        StreamConfigurations(listOf(streamConfiguration)),
        listOf(
          DestinationSyncMode.APPEND_DEDUP,
          DestinationSyncMode.OVERWRITE_DEDUP,
          DestinationSyncMode.APPEND,
          DestinationSyncMode.OVERWRITE,
          DestinationSyncMode.UPDATE,
          DestinationSyncMode.SOFT_DELETE,
        ),
      )

    assertEquals(true, configuredStreams.first().config.selected)
    assertEquals(getSyncMode(connectionSyncMode), configuredStreams.first().config.syncMode)
    assertEquals(getDestinationSyncMode(connectionSyncMode), configuredStreams.first().config.destinationSyncMode)
  }

  @Test
  internal fun `test that when a stream configuration does not have a configured sync mode, the updated configuration uses full refresh overwrite`() {
    val cursorField = "cursor"
    val primayKeyColumn = "primary"
    val streamConfiguration =
      StreamConfiguration(
        name = "name1",
        syncMode = null,
        cursorField = listOf(cursorField),
        primaryKey = listOf(listOf(primayKeyColumn)),
      )

    val configuredStreams =
      AirbyteCatalogHelper.getValidConfiguredStreams(
        createAirbyteCatalog(),
        StreamConfigurations(listOf(streamConfiguration)),
        listOf(DestinationSyncMode.APPEND_DEDUP, DestinationSyncMode.OVERWRITE_DEDUP, DestinationSyncMode.APPEND, DestinationSyncMode.OVERWRITE),
      )

    assertEquals(true, configuredStreams.first().config.selected)
    assertEquals(SyncMode.FULL_REFRESH, configuredStreams.first().config.syncMode)
    assertEquals(DestinationSyncMode.OVERWRITE, configuredStreams.first().config.destinationSyncMode)
  }

  @Test
  internal fun `test that if the stream configuration contains an invalid sync mode, the stream is considered invalid`() {
    val airbyteStream = AirbyteStream()
    airbyteStream.supportedSyncModes = listOf(SyncMode.INCREMENTAL)
    val streamConfiguration =
      StreamConfiguration(
        name = "stream-name",
        syncMode = ConnectionSyncModeEnum.FULL_REFRESH_APPEND,
      )
    val throwable =
      assertThrows(BadRequestProblem::class.java) {
        AirbyteCatalogHelper.validateStreamConfig(
          streamConfiguration = streamConfiguration,
          validDestinationSyncModes = listOf(DestinationSyncMode.OVERWRITE),
          airbyteStream = airbyteStream,
        )
      }
    val problemData: ProblemMessageData = throwable.problem.data as ProblemMessageData
    assertEquals(true, problemData.message.contains("Cannot set sync mode to ${streamConfiguration.syncMode} for stream"))
  }

  @Test
  internal fun `test that a stream configuration with FULL_REFRESH_APPEND is always considered to be valid`() {
    val airbyteStream = AirbyteStream()
    val streamConfiguration =
      StreamConfiguration(
        name = "name1",
        syncMode = ConnectionSyncModeEnum.FULL_REFRESH_APPEND,
      )
    airbyteStream.supportedSyncModes = listOf(SyncMode.FULL_REFRESH)

    val configuredStreams =
      AirbyteCatalogHelper.getValidConfiguredStreams(
        createAirbyteCatalog(),
        StreamConfigurations(listOf(streamConfiguration)),
        listOf(DestinationSyncMode.APPEND),
      )

    assertTrue(
      AirbyteCatalogHelper.validateStreamConfig(
        streamConfiguration = streamConfiguration,
        validDestinationSyncModes = listOf(DestinationSyncMode.APPEND),
        airbyteStream = airbyteStream,
      ),
    )
    assertEquals(SyncMode.FULL_REFRESH, configuredStreams.first().config.syncMode)
    assertEquals(DestinationSyncMode.APPEND, configuredStreams.first().config.destinationSyncMode)
  }

  @Test
  internal fun `test that a stream configuration with FULL_REFRESH_OVERWRITE is always considered to be valid`() {
    val airbyteStream = AirbyteStream()
    val streamConfiguration =
      StreamConfiguration(
        name = "name1",
        syncMode = ConnectionSyncModeEnum.FULL_REFRESH_OVERWRITE,
      )
    airbyteStream.supportedSyncModes = listOf(SyncMode.FULL_REFRESH)

    val configuredStreams =
      AirbyteCatalogHelper.getValidConfiguredStreams(
        createAirbyteCatalog(),
        StreamConfigurations(listOf(streamConfiguration)),
        listOf(DestinationSyncMode.APPEND),
      )

    assertTrue(
      AirbyteCatalogHelper.validateStreamConfig(
        streamConfiguration = streamConfiguration,
        validDestinationSyncModes = listOf(DestinationSyncMode.OVERWRITE),
        airbyteStream = airbyteStream,
      ),
    )
    assertEquals(SyncMode.FULL_REFRESH, configuredStreams.first().config.syncMode)
    assertEquals(DestinationSyncMode.OVERWRITE, configuredStreams.first().config.destinationSyncMode)
  }

  @Test
  internal fun `test that a stream configuration with INCREMENTAL_APPEND is only valid if the source defined cursor field is also valid`() {
    val cursorField = "cursor"
    val airbyteStream = AirbyteStream()
    val airbyteStreamConfiguration = createAirbyteStreamConfiguration()
    val streamConfiguration =
      StreamConfiguration(
        name = "name",
        syncMode = ConnectionSyncModeEnum.INCREMENTAL_APPEND,
        cursorField = listOf(cursorField),
      )
    airbyteStream.defaultCursorField = listOf(cursorField)
    airbyteStream.sourceDefinedCursor = true
    airbyteStream.supportedSyncModes = listOf(SyncMode.INCREMENTAL)

    assertTrue(
      AirbyteCatalogHelper.validateStreamConfig(
        streamConfiguration = streamConfiguration,
        validDestinationSyncModes = listOf(DestinationSyncMode.APPEND),
        airbyteStream = airbyteStream,
      ),
    )
    assertEquals(SyncMode.INCREMENTAL, airbyteStreamConfiguration.syncMode)
    assertEquals(DestinationSyncMode.APPEND, airbyteStreamConfiguration.destinationSyncMode)
    assertEquals(listOf(cursorField), airbyteStreamConfiguration.cursorField)
  }

  @Test
  internal fun `test setting a cursor when a source defined cursor already exists`() {
    val cursorField = "cursor"
    val streamName = "name1"
    val airbyteStream = AirbyteStream()
    val streamConfiguration =
      StreamConfiguration(
        name = streamName,
        syncMode = ConnectionSyncModeEnum.INCREMENTAL_APPEND,
        cursorField = listOf("other"),
      )
    airbyteStream.defaultCursorField = listOf(cursorField)
    airbyteStream.name = streamName
    airbyteStream.sourceDefinedCursor = true
    airbyteStream.supportedSyncModes = listOf(SyncMode.INCREMENTAL)

    val throwable =
      assertThrows(BadRequestProblem::class.java) {
        AirbyteCatalogHelper.getValidConfiguredStreams(
          createAirbyteCatalog(sourceDefinedCursor = true, jsonSchema = cursorPrimaryKeyJsonSchema()),
          StreamConfigurations(listOf(streamConfiguration)),
          listOf(DestinationSyncMode.APPEND),
        )
      }
    val problemData: ProblemMessageData = throwable.problem.data as ProblemMessageData
    assertEquals(true, problemData.message.contains("Do not include a cursor field configuration for this stream"))
  }

  @Test
  internal fun `test that a stream configuration with INCREMENTAL_APPEND is only valid if the source cursor field is also valid`() {
    val cursorField = "cursor"
    val airbyteStream = AirbyteStream()
    val streamConfiguration =
      StreamConfiguration(
        name = "name1",
        syncMode = ConnectionSyncModeEnum.INCREMENTAL_APPEND,
        cursorField = listOf(cursorField),
      )
    airbyteStream.defaultCursorField = listOf(cursorField)
    airbyteStream.jsonSchema = Jsons.deserialize("{\"properties\": {\"$cursorField\": {}}}")
    airbyteStream.sourceDefinedCursor = false
    airbyteStream.supportedSyncModes = listOf(SyncMode.INCREMENTAL)

    val configuredStreams =
      AirbyteCatalogHelper.getValidConfiguredStreams(
        createAirbyteCatalog(sourceDefinedCursor = true, jsonSchema = cursorPrimaryKeyJsonSchema()),
        StreamConfigurations(listOf(streamConfiguration)),
        listOf(DestinationSyncMode.APPEND),
      )

    assertTrue(
      AirbyteCatalogHelper.validateStreamConfig(
        streamConfiguration = streamConfiguration,
        validDestinationSyncModes = listOf(DestinationSyncMode.APPEND),
        airbyteStream = airbyteStream,
      ),
    )
    assertEquals(SyncMode.INCREMENTAL, configuredStreams.first().config.syncMode)
    assertEquals(DestinationSyncMode.APPEND, configuredStreams.first().config.destinationSyncMode)
    assertEquals(listOf(cursorField), configuredStreams.first().config.cursorField)
  }

  @Test
  internal fun `test that a stream configuration with INCREMENTAL_APPEND is invalid if the source cursor field is invalid`() {
    val cursorField = "cursor"
    val otherCursorField = "other"
    val airbyteStream = AirbyteStream()
    val streamConfiguration =
      StreamConfiguration(
        name = "name",
        syncMode = ConnectionSyncModeEnum.INCREMENTAL_APPEND,
        cursorField = listOf(cursorField),
      )
    airbyteStream.defaultCursorField = listOf(otherCursorField)
    airbyteStream.jsonSchema = Jsons.deserialize("{\"properties\": {\"$otherCursorField\": {}}}")
    airbyteStream.name = "name"
    airbyteStream.sourceDefinedCursor = false
    airbyteStream.supportedSyncModes = listOf(SyncMode.INCREMENTAL)

    val throwable =
      assertThrows(BadRequestProblem::class.java) {
        AirbyteCatalogHelper.validateStreamConfig(
          streamConfiguration = streamConfiguration,
          validDestinationSyncModes = listOf(DestinationSyncMode.APPEND),
          airbyteStream = airbyteStream,
        )
      }
    val problemData: ProblemMessageData = throwable.problem.data as ProblemMessageData
    assertEquals(
      true,
      problemData.message.contains(
        "Invalid cursor field for stream: ${airbyteStream.name}. The list of valid cursor fields include: [[$otherCursorField]]",
      ),
    )
  }

  @Test
  internal fun `test that a stream configuration with INCREMENTAL_APPEND is invalid if there is no cursor field`() {
    val cursorField = "cursor"
    val airbyteStream = AirbyteStream()
    val streamConfiguration =
      StreamConfiguration(
        name = "name",
        syncMode = ConnectionSyncModeEnum.INCREMENTAL_APPEND,
        cursorField = listOf(),
      )
    airbyteStream.defaultCursorField = listOf()
    airbyteStream.jsonSchema = Jsons.deserialize("{\"properties\": {\"$cursorField\": {}}}")
    airbyteStream.name = "name"
    airbyteStream.sourceDefinedCursor = false
    airbyteStream.supportedSyncModes = listOf(SyncMode.INCREMENTAL)

    val throwable =
      assertThrows(BadRequestProblem::class.java) {
        AirbyteCatalogHelper.validateStreamConfig(
          streamConfiguration = streamConfiguration,
          validDestinationSyncModes = listOf(DestinationSyncMode.APPEND),
          airbyteStream = airbyteStream,
        )
      }
    val problemData: ProblemMessageData = throwable.problem.data as ProblemMessageData
    assertEquals(
      true,
      problemData.message.contains(
        "No default cursor field for stream: ${airbyteStream.name}. Please include a cursor field configuration for this stream.",
      ),
    )
  }

  @Test
  internal fun `test that an INCREMENTAL_DEDUPED_HISTORY stream is only valid if the source defined cursor and primary key field are also valid`() {
    val cursorField = "cursor"
    val primaryKey = "primary"
    val airbyteStream = AirbyteStream()
    val streamConfiguration =
      StreamConfiguration(
        name = "name1",
        syncMode = ConnectionSyncModeEnum.INCREMENTAL_DEDUPED_HISTORY,
        cursorField = listOf(cursorField),
        primaryKey = listOf(listOf(primaryKey)),
      )
    airbyteStream.defaultCursorField = listOf(cursorField)
    airbyteStream.jsonSchema = Jsons.deserialize("{\"properties\": {\"$cursorField\": {}, \"$primaryKey\": {}}}")
    airbyteStream.sourceDefinedCursor = true
    airbyteStream.supportedSyncModes = listOf(SyncMode.INCREMENTAL)

    val configuredStreams =
      AirbyteCatalogHelper.getValidConfiguredStreams(
        createAirbyteCatalog(sourceDefinedCursor = true, jsonSchema = cursorPrimaryKeyJsonSchema()),
        StreamConfigurations(listOf(streamConfiguration)),
        listOf(DestinationSyncMode.APPEND_DEDUP),
      )

    assertTrue(
      AirbyteCatalogHelper.validateStreamConfig(
        streamConfiguration = streamConfiguration,
        validDestinationSyncModes = listOf(DestinationSyncMode.APPEND_DEDUP),
        airbyteStream = airbyteStream,
      ),
    )
    assertEquals(SyncMode.INCREMENTAL, configuredStreams.first().config.syncMode)
    assertEquals(DestinationSyncMode.APPEND_DEDUP, configuredStreams.first().config.destinationSyncMode)
    assertEquals(listOf(cursorField), configuredStreams.first().config.cursorField)
    assertEquals(listOf(listOf(primaryKey)), configuredStreams.first().config.primaryKey)
  }

  @Test
  internal fun `test that an INCREMENTAL_DEDUPED_HISTORY stream is only valid if the source cursor field and primary key field are also valid`() {
    val cursorField = "cursor"
    val primaryKey = "primary"
    val airbyteStream = AirbyteStream()
    val streamConfiguration =
      StreamConfiguration(
        name = "name1",
        syncMode = ConnectionSyncModeEnum.INCREMENTAL_DEDUPED_HISTORY,
        cursorField = listOf(cursorField),
        primaryKey = listOf(listOf(primaryKey)),
      )
    airbyteStream.defaultCursorField = listOf(cursorField)
    airbyteStream.jsonSchema = Jsons.deserialize("{\"properties\": {\"$cursorField\": {}, \"$primaryKey\": {}}}")
    airbyteStream.sourceDefinedCursor = false
    airbyteStream.supportedSyncModes = listOf(SyncMode.INCREMENTAL)

    val configuredStreams =
      AirbyteCatalogHelper.getValidConfiguredStreams(
        createAirbyteCatalog(sourceDefinedCursor = true, jsonSchema = cursorPrimaryKeyJsonSchema()),
        StreamConfigurations(listOf(streamConfiguration)),
        listOf(DestinationSyncMode.APPEND_DEDUP),
      )

    assertTrue(
      AirbyteCatalogHelper.validateStreamConfig(
        streamConfiguration = streamConfiguration,
        validDestinationSyncModes = listOf(DestinationSyncMode.APPEND_DEDUP),
        airbyteStream = airbyteStream,
      ),
    )
    assertEquals(SyncMode.INCREMENTAL, configuredStreams.first().config.syncMode)
    assertEquals(DestinationSyncMode.APPEND_DEDUP, configuredStreams.first().config.destinationSyncMode)
    assertEquals(listOf(cursorField), configuredStreams.first().config.cursorField)
    assertEquals(listOf(listOf(primaryKey)), configuredStreams.first().config.primaryKey)
  }

  @Test
  internal fun `test that the combined sync modes are valid`() {
    val validSourceSyncModes = listOf(SyncMode.FULL_REFRESH)
    val validDestinationSyncModes = listOf(DestinationSyncMode.OVERWRITE)

    val combinedSyncModes =
      AirbyteCatalogHelper.validCombinedSyncModes(
        validSourceSyncModes = validSourceSyncModes,
        validDestinationSyncModes = validDestinationSyncModes,
      )
    assertEquals(1, combinedSyncModes.size)
    assertEquals(listOf(ConnectionSyncModeEnum.FULL_REFRESH_OVERWRITE).first(), combinedSyncModes.first())
  }

  @Test
  internal fun `test that the updated configuration includes destination object names`() {
    val streamConfiguration =
      StreamConfiguration(
        name = "name1",
        destinationObjectName = "destination_object_name",
      )

    val configuredStreams =
      AirbyteCatalogHelper.getValidConfiguredStreams(
        createAirbyteCatalog(true, jsonSchema = cursorPrimaryKeyJsonSchema()),
        StreamConfigurations(listOf(streamConfiguration)),
        listOf(DestinationSyncMode.APPEND_DEDUP),
      )

    assertEquals("destination_object_name", configuredStreams.first().config.destinationObjectName)
  }

  @Test
  internal fun `test that the updated configuration includes files if set`() {
    val streamConfiguration =
      StreamConfiguration(
        name = "name1",
        includeFiles = true,
      )

    val configuredStreams =
      AirbyteCatalogHelper.getValidConfiguredStreams(
        createAirbyteCatalog(true, jsonSchema = cursorPrimaryKeyJsonSchema()),
        StreamConfigurations(listOf(streamConfiguration)),
        listOf(DestinationSyncMode.APPEND_DEDUP),
      )

    assertTrue(configuredStreams.first().config.includeFiles)
  }

  @Test
  internal fun `test that the updated configuration includes configured mappers if provided`() {
    val streamConfiguration =
      StreamConfiguration(
        name = "name1",
        mappers =
          listOf(
            ConfiguredStreamMapper(StreamMapperType.HASHING, Jsons.emptyObject()),
            ConfiguredStreamMapper(StreamMapperType.FIELD_MINUS_RENAMING, Jsons.emptyObject()),
          ),
      )

    val configuredStreams =
      AirbyteCatalogHelper.getValidConfiguredStreams(
        createAirbyteCatalog(true),
        StreamConfigurations(listOf(streamConfiguration)),
        listOf(DestinationSyncMode.APPEND_DEDUP),
      )

    assertEquals(
      2,
      configuredStreams
        .first()
        .config.mappers.size,
    )
    assertEquals(
      io.airbyte.api.model.generated.StreamMapperType.HASHING,
      configuredStreams
        .first()
        .config.mappers[0]
        .type,
    )
    assertEquals(
      io.airbyte.api.model.generated.StreamMapperType.FIELD_RENAMING,
      configuredStreams
        .first()
        .config.mappers[1]
        .type,
    )
  }

  @Test
  internal fun `test that old mappers are kept if mappers are not provided`() {
    val streamConfiguration =
      StreamConfiguration(
        name = "name",
        mappers = null,
      )

    val discoverStream = createAirbyteStreamConfiguration()
    discoverStream.mappers =
      listOf(
        io.airbyte.api.model.generated
          .ConfiguredStreamMapper()
          .type(io.airbyte.api.model.generated.StreamMapperType.HASHING),
      )

    val discoverCatalog = createAirbyteCatalog()
    discoverCatalog.streams =
      listOf(
        AirbyteStreamAndConfiguration().apply {
          stream = createAirbyteStream()
          config = discoverStream
        },
      )

    val configuredStreams =
      AirbyteCatalogHelper.getValidConfiguredStreams(
        discoverCatalog,
        StreamConfigurations(listOf(streamConfiguration)),
        listOf(),
      )

    assertEquals(
      1,
      configuredStreams
        .first()
        .config.mappers.size,
    )
    assertEquals(
      io.airbyte.api.model.generated.StreamMapperType.HASHING,
      configuredStreams
        .first()
        .config.mappers[0]
        .type,
    )
  }

  @Nested
  inner class ValidateFieldSelection {
    private val fieldTestingJsonSchemaString =
      """
      {
          "type": "object",
          "properties": {
            "f1": {
              "type": [
                "null",
                "string"
              ]
            },
            "m1": {
              "type": [
                "null",
                "string"
              ]
            },
            "y1": {
              "type": [
                "null",
                "string"
              ],
              "properties": {
                  "url": {
                    "type": ["null", "string"]
                  }
              }
            },
            "b1": {
              "type": [
                "null",
                "string"
              ]
            }
          }
        }
      """.trimIndent()

    private val schemaConfiguration = AirbyteStreamConfiguration()
    private val sourceStream = AirbyteStream()

    @BeforeEach
    fun setUp() {
      schemaConfiguration.destinationSyncMode = DestinationSyncMode.OVERWRITE
      schemaConfiguration.fieldSelectionEnabled = null
      schemaConfiguration.selectedFields = null
      sourceStream.jsonSchema = Jsons.deserialize(fieldTestingJsonSchemaString)
      sourceStream.defaultCursorField = listOf("b1")
      sourceStream.sourceDefinedPrimaryKey = listOf(listOf("f1"))
    }

    @Test
    fun `Selected fields data is provided in the request, should be included in the updated config`() {
      val streamConfiguration =
        StreamConfiguration(
          name = "name1",
          selectedFields =
            listOf(
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf("f1")),
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf("m1")),
            ),
        )
      val configuredStreams =
        AirbyteCatalogHelper.getValidConfiguredStreams(
          createAirbyteCatalog(jsonSchema = Jsons.deserialize(fieldTestingJsonSchemaString)),
          StreamConfigurations(listOf(streamConfiguration)),
          listOf(),
        )

      assertEquals(true, configuredStreams.first().config.selected)
      assertEquals(
        2,
        configuredStreams
          .first()
          .config.selectedFields.size,
      )
    }

    @Test
    fun `Selected fields data is not provided in the request, should use the original config`() {
      val streamConfiguration =
        StreamConfiguration(
          name = "name1",
          selectedFields = null,
        )
      val configuredStreams =
        AirbyteCatalogHelper.getValidConfiguredStreams(
          createAirbyteCatalog(jsonSchema = Jsons.deserialize(fieldTestingJsonSchemaString)),
          StreamConfigurations(listOf(streamConfiguration)),
          listOf(),
        )

      // Defaults from the createAirbyteCatalog stream
      assertEquals(listOf(SelectedFieldInfo()), configuredStreams.first().config.selectedFields)
      assertEquals(true, configuredStreams.first().config.fieldSelectionEnabled)
    }

    @Test
    fun `Should bypass validation if selected fields are not being set specifically`() {
      val streamConfiguration =
        StreamConfiguration(
          name = "name1",
          selectedFields = null,
        )
      assertDoesNotThrow { AirbyteCatalogHelper.validateFieldSelection(streamConfiguration, sourceStream) }
    }

    @Test
    fun `Should throw error if input selected fields is set to an empty list`() {
      val streamConfiguration =
        StreamConfiguration(
          name = "name1",
          selectedFields = listOf(),
        )
      val throwable =
        assertThrows(BadRequestProblem::class.java) {
          AirbyteCatalogHelper.validateFieldSelection(streamConfiguration, sourceStream)
        }
      val problemData: ProblemMessageData = throwable.problem.data as ProblemMessageData
      assertEquals(true, problemData.message.contains("No fields selected"))
    }

    @Test
    fun `Should throw error if any selected field contains empty field path`() {
      val streamConfiguration =
        StreamConfiguration(
          name = "name1",
          selectedFields =
            listOf(
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf("m1")),
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf()),
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf("b1")),
            ),
        )
      val throwable =
        assertThrows(BadRequestProblem::class.java) {
          AirbyteCatalogHelper.validateFieldSelection(streamConfiguration, sourceStream)
        }
      val problemData: ProblemMessageData = throwable.problem.data as ProblemMessageData
      assertEquals(true, problemData.message.contains("Selected field path cannot be empty"))
    }

    @Test
    fun `Should throw error if any selected field contains nested field path`() {
      val streamConfiguration =
        StreamConfiguration(
          name = "testStream",
          selectedFields =
            listOf(
              // f1 -> f2 -> f3 is a nested field path
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf("f1", "f2", "f3")),
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf("b1")),
            ),
        )
      val throwable =
        assertThrows(BadRequestProblem::class.java) {
          AirbyteCatalogHelper.validateFieldSelection(streamConfiguration, sourceStream)
        }
      val problemData: ProblemMessageData = throwable.problem.data as ProblemMessageData
      assertEquals(true, problemData.message.contains("Nested field selection not supported"))
    }

    @Test
    fun `Should throw error if input contains duplicate field names`() {
      val streamConfiguration =
        StreamConfiguration(
          name = "testStream",
          selectedFields =
            listOf(
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf("f1")),
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf("m1")),
              // `m1` is a dup field
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf("m1")),
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf("b1")),
            ),
        )
      val throwable =
        assertThrows(BadRequestProblem::class.java) {
          AirbyteCatalogHelper.validateFieldSelection(streamConfiguration, sourceStream)
        }
      val problemData: ProblemMessageData = throwable.problem.data as ProblemMessageData
      assertEquals(true, problemData.message.contains("Duplicate fields selected"))
    }

    @Test
    fun `Should throw error if input contains non-existed field names`() {
      val streamConfiguration =
        StreamConfiguration(
          name = "testStream",
          selectedFields =
            listOf(
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf("f1")),
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf("m1")),
              // `x1` is not existed in source schema
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf("x1")),
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf("b1")),
            ),
        )
      val throwable =
        assertThrows(BadRequestProblem::class.java) {
          AirbyteCatalogHelper.validateFieldSelection(streamConfiguration, sourceStream)
        }
      val problemData: ProblemMessageData = throwable.problem.data as ProblemMessageData
      assertEquals(true, problemData.message.contains("Invalid fields selected"))
    }

    @Test
    fun `Should throw error if primary key(s) are not selected in incremental_dedup mode`() {
      val streamConfiguration =
        StreamConfiguration(
          name = "testStream",
          syncMode = ConnectionSyncModeEnum.INCREMENTAL_DEDUPED_HISTORY,
          selectedFields =
            listOf(
              // "f1" as primary key is missing
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf("m1")),
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf("b1")),
            ),
        )
      val throwable =
        assertThrows(BadRequestProblem::class.java) {
          AirbyteCatalogHelper.validateFieldSelection(streamConfiguration, sourceStream)
        }
      val problemData: ProblemMessageData = throwable.problem.data as ProblemMessageData
      assertEquals(true, problemData.message.contains("Primary key fields are not selected properly"))
    }

    @Test
    fun `Should throw error if primary key(s) are not selected in full_refresh_overwrite_deduped mode`() {
      val streamConfiguration =
        StreamConfiguration(
          name = "testStream",
          syncMode = ConnectionSyncModeEnum.FULL_REFRESH_OVERWRITE_DEDUPED,
          selectedFields =
            listOf(
              // "f1" as primary key is missing
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf("m1")),
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf("b1")),
            ),
        )
      val throwable =
        assertThrows(BadRequestProblem::class.java) {
          AirbyteCatalogHelper.validateFieldSelection(streamConfiguration, sourceStream)
        }
      val problemData: ProblemMessageData = throwable.problem.data as ProblemMessageData
      assertEquals(true, problemData.message.contains("Primary key fields are not selected properly"))
    }

    @Test
    fun `Should throw error if cursor field is not selected in incremental_dedup mode`() {
      val streamConfiguration =
        StreamConfiguration(
          name = "testStream",
          syncMode = ConnectionSyncModeEnum.INCREMENTAL_DEDUPED_HISTORY,
          selectedFields =
            listOf(
              // "b1" as cursor field is missing
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf("f1")),
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf("m1")),
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf("y1")),
            ),
        )
      val throwable =
        assertThrows(BadRequestProblem::class.java) {
          AirbyteCatalogHelper.validateFieldSelection(streamConfiguration, sourceStream)
        }
      val problemData: ProblemMessageData = throwable.problem.data as ProblemMessageData
      assertEquals(true, problemData.message.contains("Cursor field is not selected properly"))
    }

    @Test
    fun `Should throw error if cursor field is not selected in incremental_append mode`() {
      val streamConfiguration =
        StreamConfiguration(
          name = "testStream",
          syncMode = ConnectionSyncModeEnum.INCREMENTAL_APPEND,
          selectedFields =
            listOf(
              // "b1" as cursor field is missing
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf("f1")),
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf("m1")),
              io.airbyte.publicApi.server.generated.models
                .SelectedFieldInfo(fieldPath = listOf("y1")),
            ),
        )
      val throwable =
        assertThrows(BadRequestProblem::class.java) {
          AirbyteCatalogHelper.validateFieldSelection(streamConfiguration, sourceStream)
        }
      val problemData: ProblemMessageData = throwable.problem.data as ProblemMessageData
      assertEquals(true, problemData.message.contains("Cursor field is not selected properly"))
    }
  }

  private fun createAirbyteCatalog(
    sourceDefinedCursor: Boolean = false,
    jsonSchema: JsonNode? = null,
  ): AirbyteCatalog {
    val airbyteCatalog = AirbyteCatalog()
    val streams = mutableListOf<AirbyteStreamAndConfiguration>()
    for (i in 1..5) {
      val streamAndConfiguration = AirbyteStreamAndConfiguration()
      val stream = createAirbyteStream(i.toString(), sourceDefinedCursor, jsonSchema)
      streamAndConfiguration.stream = stream
      streamAndConfiguration.config = createAirbyteStreamConfiguration()
      streams += streamAndConfiguration
    }
    airbyteCatalog.streams(streams)
    return airbyteCatalog
  }

  private fun cursorPrimaryKeyJsonSchema(): JsonNode =
    Jsons.deserialize("{\"properties\": {\"cursor\": {\"type\": \"object\"}, \"primary\": {\"type\": \"string\"}}}")

  private fun createAirbyteStream(
    suffix: String = "",
    sourceDefinedCursor: Boolean = false,
    jsonSchema: JsonNode? = null,
  ): AirbyteStream =
    AirbyteStream().apply {
      this.name = "name$suffix"
      this.namespace = "namespace"
      this.supportedSyncModes(listOf(SyncMode.FULL_REFRESH, SyncMode.INCREMENTAL))
      this.jsonSchema = jsonSchema
      this.sourceDefinedCursor = sourceDefinedCursor
      this.defaultCursorField = if (sourceDefinedCursor) listOf("cursor") else null
    }

  private fun createAirbyteStreamConfiguration(): AirbyteStreamConfiguration {
    val airbyteStreamConfiguration = AirbyteStreamConfiguration()
    airbyteStreamConfiguration.aliasName = "alias"
    airbyteStreamConfiguration.cursorField = listOf("cursor")
    airbyteStreamConfiguration.destinationSyncMode = DestinationSyncMode.APPEND
    airbyteStreamConfiguration.fieldSelectionEnabled = true
    airbyteStreamConfiguration.primaryKey = listOf(listOf("primary"))
    airbyteStreamConfiguration.selected = false
    airbyteStreamConfiguration.selectedFields = listOf(SelectedFieldInfo())
    airbyteStreamConfiguration.mappers =
      listOf(
        io.airbyte.api.model.generated
          .ConfiguredStreamMapper(),
      )
    airbyteStreamConfiguration.suggested = false
    airbyteStreamConfiguration.syncMode = SyncMode.INCREMENTAL
    airbyteStreamConfiguration.namespace = "namespace"
    return airbyteStreamConfiguration
  }

  private fun getSyncMode(connectionSyncMode: ConnectionSyncModeEnum): SyncMode =
    when (connectionSyncMode) {
      ConnectionSyncModeEnum.FULL_REFRESH_OVERWRITE -> SyncMode.FULL_REFRESH
      ConnectionSyncModeEnum.FULL_REFRESH_APPEND -> SyncMode.FULL_REFRESH
      ConnectionSyncModeEnum.INCREMENTAL_APPEND -> SyncMode.INCREMENTAL
      ConnectionSyncModeEnum.INCREMENTAL_DEDUPED_HISTORY -> SyncMode.INCREMENTAL
      ConnectionSyncModeEnum.FULL_REFRESH_OVERWRITE_DEDUPED -> SyncMode.FULL_REFRESH
      ConnectionSyncModeEnum.INCREMENTAL_UPDATE -> SyncMode.INCREMENTAL
      ConnectionSyncModeEnum.FULL_REFRESH_UPDATE -> SyncMode.FULL_REFRESH
      ConnectionSyncModeEnum.INCREMENTAL_SOFT_DELETE -> SyncMode.INCREMENTAL
      ConnectionSyncModeEnum.FULL_REFRESH_SOFT_DELETE -> SyncMode.FULL_REFRESH
    }

  private fun getDestinationSyncMode(connectionSyncMode: ConnectionSyncModeEnum): DestinationSyncMode =
    when (connectionSyncMode) {
      ConnectionSyncModeEnum.FULL_REFRESH_OVERWRITE -> DestinationSyncMode.OVERWRITE
      ConnectionSyncModeEnum.FULL_REFRESH_APPEND -> DestinationSyncMode.APPEND
      ConnectionSyncModeEnum.INCREMENTAL_APPEND -> DestinationSyncMode.APPEND
      ConnectionSyncModeEnum.INCREMENTAL_DEDUPED_HISTORY -> DestinationSyncMode.APPEND_DEDUP
      ConnectionSyncModeEnum.FULL_REFRESH_OVERWRITE_DEDUPED -> DestinationSyncMode.OVERWRITE_DEDUP
      ConnectionSyncModeEnum.INCREMENTAL_UPDATE -> DestinationSyncMode.UPDATE
      ConnectionSyncModeEnum.FULL_REFRESH_UPDATE -> DestinationSyncMode.UPDATE
      ConnectionSyncModeEnum.INCREMENTAL_SOFT_DELETE -> DestinationSyncMode.SOFT_DELETE
      ConnectionSyncModeEnum.FULL_REFRESH_SOFT_DELETE -> DestinationSyncMode.SOFT_DELETE
    }
}
