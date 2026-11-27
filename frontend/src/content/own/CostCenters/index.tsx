import { useContext, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation } from 'react-router-dom';
import MultipleTabsLayout from '../components/MultipleTabsLayout';
import PermissionErrorMessage from '../components/PermissionErrorMessage';
import { TitleContext } from '../../../contexts/TitleContext';
import useAuth from '../../../hooks/useAuth';
import { PermissionEntity } from '../../../models/owns/role';
import CostCentersTable from './CostCentersTable';
import Profitability from './Profitability';

function CostCenters() {
  const { t }: { t: any } = useTranslation();
  const [openAddModal, setOpenAddModal] = useState<boolean>(false);
  const { setTitle } = useContext(TitleContext);
  const { hasViewPermission, hasCreatePermission } = useAuth();
  const location = useLocation();

  useEffect(() => {
    setTitle(t('cost_centers_budgets'));
  }, []);

  const tabs = [
    { value: 'budgets', label: t('cost_center_tab_centers') },
    { value: 'profitability', label: t('cost_center_tab_profitability') }
  ];
  const lastSegment = location.pathname.split('/').filter(Boolean).pop();
  const foundIndex = tabs.findIndex((tab) => tab.value === lastSegment);
  const tabIndex = foundIndex === -1 ? 0 : foundIndex;

  if (!hasViewPermission(PermissionEntity.ANALYTICS))
    return <PermissionErrorMessage message="no_access_cost_centers" />;

  return (
    <MultipleTabsLayout
      basePath="/app/cost-centers"
      tabs={tabs}
      tabIndex={tabIndex}
      title={t('cost_centers_budgets')}
      action={
        tabIndex === 0 && hasCreatePermission(PermissionEntity.SETTINGS)
          ? () => setOpenAddModal(true)
          : null
      }
      actionTitle={t('cost_center_add')}
    >
      {tabIndex === 0 ? (
        <CostCentersTable
          openModal={openAddModal}
          handleCloseModal={() => setOpenAddModal(false)}
        />
      ) : (
        <Profitability />
      )}
    </MultipleTabsLayout>
  );
}

export default CostCenters;
