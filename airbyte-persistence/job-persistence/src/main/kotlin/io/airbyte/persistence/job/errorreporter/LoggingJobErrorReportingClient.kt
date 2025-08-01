/*
 * Copyright (c) 2020-2025 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.persistence.job.errorreporter

import io.airbyte.config.FailureReason
import io.airbyte.config.StandardWorkspace
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Log job error reports.
 */
class LoggingJobErrorReportingClient : JobErrorReportingClient {
  override fun reportJobFailureReason(
    workspace: StandardWorkspace?,
    reason: FailureReason,
    dockerImage: String?,
    metadata: Map<String?, String?>?,
    attemptConfig: AttemptConfigReportingContext?,
  ) {
    LOGGER.info(
      "Report Job Error -> workspaceId: {}, dockerImage: {}, failureReason: {}, metadata: {}, state: {}, sourceConfig: {}, destinationConfig: {}",
      if (workspace != null) workspace.workspaceId else "null",
      dockerImage,
      reason,
      metadata,
      if (attemptConfig != null) attemptConfig.state else "null",
      if (attemptConfig != null) attemptConfig.sourceConfig else "null",
      if (attemptConfig != null) attemptConfig.destinationConfig else "null",
    )
  }

  companion object {
    private val LOGGER: Logger = LoggerFactory.getLogger(LoggingJobErrorReportingClient::class.java)
  }
}
