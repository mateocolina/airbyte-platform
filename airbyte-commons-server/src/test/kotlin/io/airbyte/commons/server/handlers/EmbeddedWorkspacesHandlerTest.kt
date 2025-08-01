/*
 * Copyright (c) 2020-2025 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.commons.server.handlers
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.airbyte.api.model.generated.DestinationRead
import io.airbyte.api.model.generated.WorkspaceRead
import io.airbyte.commons.DEFAULT_ORGANIZATION_ID
import io.airbyte.commons.server.handlers.EmbeddedWorkspacesHandler.Companion.embeddedEUOrganizations
import io.airbyte.config.DataplaneGroup
import io.airbyte.data.repositories.ConnectionTemplateRepository
import io.airbyte.data.repositories.WorkspaceRepository
import io.airbyte.data.repositories.entities.ConnectionTemplate
import io.airbyte.data.repositories.entities.Workspace
import io.airbyte.data.services.DataplaneGroupService
import io.airbyte.db.instance.configs.jooq.generated.enums.NamespaceDefinitionType
import io.airbyte.db.instance.configs.jooq.generated.enums.NonBreakingChangePreferenceType
import io.airbyte.db.instance.configs.jooq.generated.enums.ScheduleType
import io.airbyte.domain.models.OrganizationId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.UUID
import java.util.stream.Stream

class EmbeddedWorkspacesHandlerTest {
  private val externalUserId = "cool customer"
  private val organizationId = UUID.randomUUID()
  private val workspaceId = UUID.randomUUID()
  private val expectedCreateWorkspaceRequest =
    io.airbyte.api.model.generated
      .WorkspaceCreate()
      .name(externalUserId)
      .organizationId(organizationId)
  private val workspaceRead = WorkspaceRead().workspaceId(workspaceId)

  private val workspacesHandler = mockk<WorkspacesHandler>()
  private val workspaceRepository = mockk<WorkspaceRepository>()
  private val destinationHandler = mockk<DestinationHandler>()
  private val connectionTemplateRepository = mockk<ConnectionTemplateRepository>()
  private val dataplaneGroupService = mockk<DataplaneGroupService>()

  private val existingWorkspace =
    Workspace(
      workspaceId,
      UUID.randomUUID(),
      externalUserId,
      UUID.randomUUID().toString(),
      null,
      true,
      null,
      dataplaneGroupId = UUID.randomUUID(),
      organizationId = organizationId,
    )
  private val anotherExistingWorkspace =
    Workspace(
      UUID.randomUUID(),
      UUID.randomUUID(),
      externalUserId,
      UUID.randomUUID().toString(),
      null,
      true,
      null,
      dataplaneGroupId = UUID.randomUUID(),
      organizationId = organizationId,
    )
  private val connectionTemplate =
    ConnectionTemplate(
      UUID.randomUUID(),
      organizationId,
      "destinationName",
      UUID.randomUUID(),
      jacksonObjectMapper().readTree("{}"),
      NamespaceDefinitionType.destination,
      null,
      null,
      ScheduleType.manual,
      null,
      null,
      NonBreakingChangePreferenceType.disable,
    )

  private val destinationRead =
    DestinationRead()
      .destinationId(UUID.randomUUID())
      .workspaceId(workspaceId)
      .destinationDefinitionId(connectionTemplate.destinationDefinitionId)
      .connectionConfiguration(connectionTemplate.destinationConfig)
      .name(connectionTemplate.destinationName)

  lateinit var handler: EmbeddedWorkspacesHandler

  @BeforeEach
  fun setup() {
    handler =
      EmbeddedWorkspacesHandler(workspacesHandler, workspaceRepository, destinationHandler, connectionTemplateRepository, dataplaneGroupService)
  }

  @Test
  fun `test create`() {
    every {
      workspaceRepository.findByNameAndOrganizationIdAndTombstoneFalse(externalUserId, organizationId)
    } returns emptyList()

    every {
      workspacesHandler.createWorkspace(expectedCreateWorkspaceRequest)
    } returns workspaceRead

    every {
      connectionTemplateRepository.findByOrganizationIdAndTombstoneFalse(organizationId)
    } returns listOf(connectionTemplate)

    every {
      destinationHandler.createDestination(
        io.airbyte.api.model.generated
          .DestinationCreate()
          .name(connectionTemplate.destinationName)
          .workspaceId(workspaceId)
          .destinationDefinitionId(connectionTemplate.destinationDefinitionId)
          .connectionConfiguration(connectionTemplate.destinationConfig),
      )
    } returns destinationRead

    val returnedWorkspaceId = handler.getOrCreate(OrganizationId(organizationId), externalUserId)

    assertEquals(workspaceId, returnedWorkspaceId.value)
  }

  @ParameterizedTest
  @MethodSource("euUuidProvider")
  fun `test create EU`(orgId: UUID) {
    val dataplaneGroupIdEU = UUID.randomUUID()
    val euDataplane =
      DataplaneGroup().apply {
        id = dataplaneGroupIdEU
      }

    every {
      dataplaneGroupService.getDataplaneGroupByOrganizationIdAndName(DEFAULT_ORGANIZATION_ID, "EU")
    } returns euDataplane

    val expectedCreateWorkspaceRequestInEU =
      io.airbyte.api.model.generated
        .WorkspaceCreate()
        .name(externalUserId)
        .organizationId(orgId)
        .dataplaneGroupId(dataplaneGroupIdEU)
    every {
      workspaceRepository.findByNameAndOrganizationIdAndTombstoneFalse(externalUserId, orgId)
    } returns emptyList()

    every {
      workspacesHandler.createWorkspace(expectedCreateWorkspaceRequestInEU)
    } returns workspaceRead

    every {
      connectionTemplateRepository.findByOrganizationIdAndTombstoneFalse(orgId)
    } returns listOf(connectionTemplate)

    every {
      destinationHandler.createDestination(
        io.airbyte.api.model.generated
          .DestinationCreate()
          .name(connectionTemplate.destinationName)
          .workspaceId(workspaceId)
          .destinationDefinitionId(connectionTemplate.destinationDefinitionId)
          .connectionConfiguration(connectionTemplate.destinationConfig),
      )
    } returns destinationRead

    val returnedWorkspaceId = handler.getOrCreate(OrganizationId(orgId), externalUserId)

    assertEquals(workspaceId, returnedWorkspaceId.value)
  }

  @Test
  fun `test get existing workspace`() {
    every {
      workspaceRepository.findByNameAndOrganizationIdAndTombstoneFalse(externalUserId, organizationId)
    } returns listOf(existingWorkspace)

    val returnedWorkspaceId = handler.getOrCreate(OrganizationId(organizationId), externalUserId)

    assertEquals(workspaceId, returnedWorkspaceId.value)
  }

  @Test
  fun `test error if multiple workspaces with externalId`() {
    every {
      workspaceRepository.findByNameAndOrganizationIdAndTombstoneFalse(externalUserId, organizationId)
    } returns listOf(existingWorkspace, anotherExistingWorkspace)

    org.junit.jupiter.api.assertThrows<IllegalStateException> {
      handler.getOrCreate(OrganizationId(organizationId), externalUserId)
    }
  }

  companion object {
    @JvmStatic
    fun euUuidProvider(): Stream<UUID> = embeddedEUOrganizations.stream()
  }
}
