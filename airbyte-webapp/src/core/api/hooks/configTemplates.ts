import { useMutation, useQueryClient } from "@tanstack/react-query";

import { useExperiment } from "hooks/services/Experiment";
import { useNotificationService } from "hooks/services/Notification";

import { ApiCallOptions } from "../apiCall";
import { listConfigTemplates, getConfigTemplate, publicCreateConfigTemplate } from "../generated/AirbyteClient";
import {
  embeddedConfigTemplatesSourcesIdGetSourceConfigTemplate,
  embeddedConfigTemplatesSourcesListSourceConfigTemplates,
} from "../generated/SonarClient";
import { SCOPE_ORGANIZATION } from "../scopes";
import {
  AdvancedAuth,
  AdvancedAuthAuthFlowType,
  ConfigTemplateCreateRequestBody,
  ConfigTemplateList,
  ConfigTemplateRead,
  SourceDefinitionSpecification,
} from "../types/AirbyteClient";
import { useRequestOptions } from "../useRequestOptions";
import { useSuspenseQuery } from "../useSuspenseQuery";

const configTemplates = {
  all: [SCOPE_ORGANIZATION, "configTemplates"] as const,
  lists: () => [...configTemplates.all, "list"],
  detail: (configTemplateId: string) => [...configTemplates.all, "details", configTemplateId] as const,
};

// write a query function that will await the response from the server & convert the type to tightly match the expectation of the types currently used in the webapp.
// this is the recommended approach by one of the react-query maintainers. https://tkdodo.eu/blog/react-query-data-transformations
const convertedEmbeddedListSourceConfigTemplates = async (
  workspace_id: string,
  options: ApiCallOptions
): Promise<ConfigTemplateList> => {
  const response = await embeddedConfigTemplatesSourcesListSourceConfigTemplates({ workspace_id }, options);
  return {
    configTemplates: response.data.map((item) => ({
      ...item,
      icon: item.icon === null ? "" : item.icon,
    })),
  };
};

const toAuthFlowType = (v: string): AdvancedAuthAuthFlowType => {
  if (v === "oauth1.0" || v === "oauth2.0") {
    return v;
  }
  throw new Error(`Unsupported auth flow type: ${v}`);
};

// write a query function that will await the response from the server & convert the type to tightly match the expectation of the types currently used in the webapp.
// this is the recommended approach by one of the react-query maintainers. https://tkdodo.eu/blog/react-query-data-transformations
export const convertedEmbeddedGetSourceConfigTemplate = async (
  configTemplateId: string,
  options: ApiCallOptions
): Promise<ConfigTemplateRead> => {
  const response = await embeddedConfigTemplatesSourcesIdGetSourceConfigTemplate(configTemplateId, options);
  const raw = {
    ...response.user_config_spec,
    advancedAuth: response.user_config_spec.advanced_auth,
    advancedAuthGlobalCredentialsAvailable: Boolean(response.user_config_spec.advanced_auth),
  };
  const adv = raw.advanced_auth;

  return {
    id: response.id,
    name: response.name,
    icon: response.icon as string, // Icon should be optional. It's not in the legacy API, but I'm not worried about it because it's being deprecated.
    sourceDefinitionId: response.source_definition_id,
    configTemplateSpec: raw as unknown as SourceDefinitionSpecification,

    ...(adv && {
      advancedAuth: {
        authFlowType: toAuthFlowType(adv.auth_flow_type),
        predicateKey: adv.predicate_key,
        predicateValue: adv.predicate_value,
        oauthConfigSpecification: {
          oauthUserInputFromConnectorConfigSpecification:
            adv.oauth_config_specification?.oauth_user_input_from_connector_config_specification ?? null,
          completeOAuthOutputSpecification: adv.oauth_config_specification?.complete_oauth_output_specification ?? null,
          completeOAuthServerInputSpecification:
            adv.oauth_config_specification?.complete_oauth_server_input_specification ?? null,
          completeOAuthServerOutputSpecification:
            adv.oauth_config_specification?.complete_oauth_server_output_specification ?? null,
        },
      } as AdvancedAuth,
    }),

    advancedAuthGlobalCredentialsAvailable: Boolean(adv),
  };
};

// You cannot conditionally use a hook in React. Instead, we will always use the hook and handle the condition inside the query function.
export const useListConfigTemplates = (workspaceId: string): ConfigTemplateList => {
  const requestOptions = useRequestOptions();
  // use the feature flag to determine if the new server is enabled
  // note: you can override this by adding a `.experiments.json` file in `airbyte-webapp` and adding {"embedded.useSonarServer": true} to it!
  const isSonarServerEnabled = useExperiment("embedded.useSonarServer");

  return useSuspenseQuery(configTemplates.lists(), () => {
    if (isSonarServerEnabled) {
      return convertedEmbeddedListSourceConfigTemplates(workspaceId, requestOptions);
    }
    return listConfigTemplates({ workspaceId }, requestOptions);
  });
};

export const useGetConfigTemplate = (configTemplateId: string, workspaceId: string) => {
  const requestOptions = useRequestOptions();

  const isSonarServerEnabled = useExperiment("embedded.useSonarServer");

  return useSuspenseQuery(configTemplates.detail(configTemplateId), () => {
    if (isSonarServerEnabled) {
      return convertedEmbeddedGetSourceConfigTemplate(configTemplateId, requestOptions);
    }
    return getConfigTemplate({ configTemplateId, workspaceId }, requestOptions);
  });
};

export const useCreateConfigTemplate = () => {
  const requestOptions = useRequestOptions();
  const queryClient = useQueryClient();
  const { registerNotification } = useNotificationService();

  return useMutation(
    (configTemplate: ConfigTemplateCreateRequestBody) => {
      return publicCreateConfigTemplate(configTemplate, requestOptions);
    },
    {
      onSuccess: () => {
        queryClient.invalidateQueries(configTemplates.lists());
        registerNotification({
          id: "config-template-created",
          text: "Config template created",
          type: "success",
        });
      },
      onError: () => {
        registerNotification({
          id: "config-template-created",
          text: "Failed to create config template",
          type: "error",
        });
      },
    }
  );
};
