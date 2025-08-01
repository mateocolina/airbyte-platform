/*
 * Copyright (c) 2020-2025 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.commons.server.helpers;

import com.fasterxml.jackson.databind.JsonNode;
import io.airbyte.api.model.generated.DestinationRead;
import io.airbyte.api.model.generated.SupportState;
import io.airbyte.commons.json.Jsons;
import io.airbyte.config.DestinationConnection;
import io.airbyte.config.ResourceRequirements;
import io.airbyte.config.ScopedResourceRequirements;
import io.airbyte.config.StandardDestinationDefinition;
import io.airbyte.data.services.shared.DestinationConnectionWithCount;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class DestinationHelpers {

  public static JsonNode getTestDestinationJson() throws IOException {
    final Path path =
        Paths.get(DestinationHelpers.class.getClassLoader().getResource("json/TestImplementation.json").getPath());

    return Jsons.deserialize(Files.readString(path));
  }

  public static DestinationConnection generateDestination(final UUID destinationDefinitionId) throws IOException {
    return generateDestination(destinationDefinitionId, "my default dest name", false, null);
  }

  public static DestinationConnection generateDestination(final UUID destinationDefinitionId, final String name) throws IOException {
    return generateDestination(destinationDefinitionId, name, false, null);
  }

  public static DestinationConnection generateDestination(final UUID destinationDefinitionId, final boolean tombstone) throws IOException {
    return generateDestination(destinationDefinitionId, "my default dest name", tombstone, null);
  }

  public static DestinationConnection generateDestination(final UUID destinationDefinitionId, final ScopedResourceRequirements resourceRequirements)
      throws IOException {
    return generateDestination(destinationDefinitionId, "my default dest name", false, resourceRequirements);
  }

  public static DestinationConnection generateDestination(final UUID destinationDefinitionId,
                                                          final String name,
                                                          final boolean tombstone,
                                                          final ScopedResourceRequirements resourceRequirements)
      throws IOException {
    final UUID workspaceId = UUID.randomUUID();
    final UUID destinationId = UUID.randomUUID();

    final JsonNode implementationJson = getTestDestinationJson();

    return new DestinationConnection()
        .withName(name)
        .withWorkspaceId(workspaceId)
        .withDestinationDefinitionId(destinationDefinitionId)
        .withDestinationId(destinationId)
        .withConfiguration(implementationJson)
        .withTombstone(tombstone)
        .withResourceRequirements(resourceRequirements);
  }

  public static DestinationConnectionWithCount generateDestinationWithCount(final DestinationConnection destination) {
    return new DestinationConnectionWithCount(destination, "destination-definition", 0, null, java.util.Map.of(), true);
  }

  public static ScopedResourceRequirements getResourceRequirementsForDestination() {
    return new ScopedResourceRequirements().withDefault(new ResourceRequirements().withCpuRequest("2").withMemoryRequest("2"));
  }

  public static DestinationRead getDestinationRead(final DestinationConnection destination,
                                                   final StandardDestinationDefinition standardDestinationDefinition) {
    // sets reasonable defaults for isVersionOverrideApplied and supportState, use below method instead
    // if you want to override them.
    return getDestinationRead(destination, standardDestinationDefinition, false, true, SupportState.SUPPORTED, null);
  }

  public static DestinationRead getDestinationRead(final DestinationConnection destination,
                                                   final StandardDestinationDefinition standardDestinationDefinition,
                                                   final boolean isVersionOverrideApplied,
                                                   final boolean isEntitled,
                                                   final SupportState supportState,
                                                   final io.airbyte.api.model.generated.ScopedResourceRequirements resourceAllocation) {

    return new DestinationRead()
        .destinationDefinitionId(standardDestinationDefinition.getDestinationDefinitionId())
        .workspaceId(destination.getWorkspaceId())
        .destinationDefinitionId(destination.getDestinationDefinitionId())
        .destinationId(destination.getDestinationId())
        .connectionConfiguration(destination.getConfiguration())
        .name(destination.getName())
        .destinationName(standardDestinationDefinition.getName())
        .icon(standardDestinationDefinition.getIconUrl())
        .isVersionOverrideApplied(isVersionOverrideApplied)
        .isEntitled(isEntitled)
        .supportState(supportState)
        .resourceAllocation(resourceAllocation);
  }

}
