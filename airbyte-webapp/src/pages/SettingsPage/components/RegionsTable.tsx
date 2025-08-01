import { useIntl } from "react-intl";

import { FormLabel } from "components/forms/FormControl";
import { Box } from "components/ui/Box";
import { Table } from "components/ui/Table";

import { useCurrentOrganizationId } from "area/organization/utils/useCurrentOrganizationId";
import { useListWorkspacesInOrganization, useGetDataplaneGroup } from "core/api";

export const RegionsTable = () => {
  const organizationId = useCurrentOrganizationId();
  const { data: workspacesData } = useListWorkspacesInOrganization({ organizationId });
  const workspaces = workspacesData?.pages[0].workspaces ?? [];
  const { getDataplaneGroup } = useGetDataplaneGroup();
  const { formatMessage } = useIntl();

  return (
    <Box pb="lg">
      <FormLabel label={formatMessage({ id: "settings.organizationSettings.regions" })} htmlFor="regions" />
      <Table
        columns={[
          {
            header: formatMessage({ id: "settings.organizationSettings.regions.workspaces" }),
            accessorKey: "name",
          },
          {
            header: formatMessage({ id: "settings.organizationSettings.regions.region" }),
            accessorKey: "dataplaneGroupName",
          },
        ]}
        data={workspaces.map(({ name, dataplaneGroupId }) => ({
          name,
          dataplaneGroupName: getDataplaneGroup(dataplaneGroupId)?.name,
        }))}
        sorting={false}
        showTableToggle
      />
    </Box>
  );
};
