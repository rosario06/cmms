import { useContext, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation } from 'react-router-dom';
import MultipleTabsLayout from '../components/MultipleTabsLayout';
import PermissionErrorMessage from '../components/PermissionErrorMessage';
import { TitleContext } from '../../../contexts/TitleContext';
import useAuth from '../../../hooks/useAuth';
import { PermissionEntity } from '../../../models/owns/role';
import ServiceContracts from './ServiceContracts';
import SlaPolicies from './SlaPolicies';

function Contracts() {
  const { t }: { t: any } = useTranslation();
  const [openAddModal, setOpenAddModal] = useState<boolean>(false);
  const { setTitle } = useContext(TitleContext);
  const { hasViewPermission, hasCreatePermission } = useAuth();
  const location = useLocation();

  useEffect(() => {
    setTitle(t('contracts_and_sla'));
  }, []);

  const tabs = [
    { value: 'service-contracts', label: t('contracts') },
    { value: 'sla-policies', label: t('sla_policies') }
  ];
  const lastSegment = location.pathname.split('/').filter(Boolean).pop();
  const foundIndex = tabs.findIndex((tab) => tab.value === lastSegment);
  const tabIndex = foundIndex === -1 ? 0 : foundIndex;

  if (!hasViewPermission(PermissionEntity.ANALYTICS))
    return <PermissionErrorMessage message="no_access_contracts" />;

  return (
    <MultipleTabsLayout
      basePath="/app/contracts"
      tabs={tabs}
      tabIndex={tabIndex}
      title={t('contracts_and_sla')}
      action={
        hasCreatePermission(PermissionEntity.SETTINGS)
          ? () => setOpenAddModal(true)
          : null
      }
      actionTitle={tabIndex === 0 ? t('contract_add') : t('sla_add')}
    >
      {tabIndex === 0 ? (
        <ServiceContracts
          openModal={openAddModal}
          handleCloseModal={() => setOpenAddModal(false)}
        />
      ) : (
        <SlaPolicies
          openModal={openAddModal}
          handleCloseModal={() => setOpenAddModal(false)}
        />
      )}
    </MultipleTabsLayout>
  );
}

export default Contracts;
