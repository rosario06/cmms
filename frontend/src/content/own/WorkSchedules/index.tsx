import { useContext, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation } from 'react-router-dom';
import MultipleTabsLayout from '../components/MultipleTabsLayout';
import PermissionErrorMessage from '../components/PermissionErrorMessage';
import { TitleContext } from '../../../contexts/TitleContext';
import useAuth from '../../../hooks/useAuth';
import { PermissionEntity } from '../../../models/owns/role';
import Schedules from './Schedules';
import Holidays from './Holidays';

function WorkSchedules() {
  const { t }: { t: any } = useTranslation();
  const [openAddModal, setOpenAddModal] = useState<boolean>(false);
  const { setTitle } = useContext(TitleContext);
  const { hasViewPermission, hasCreatePermission } = useAuth();
  const location = useLocation();

  useEffect(() => {
    setTitle(t('work_schedules_and_holidays'));
  }, []);

  const tabs = [
    { value: 'schedules', label: t('work_schedule_tab_schedules') },
    { value: 'holidays', label: t('work_schedule_tab_holidays') }
  ];
  const lastSegment = location.pathname.split('/').filter(Boolean).pop();
  const foundIndex = tabs.findIndex((tab) => tab.value === lastSegment);
  const tabIndex = foundIndex === -1 ? 0 : foundIndex;

  if (!hasViewPermission(PermissionEntity.ANALYTICS))
    return <PermissionErrorMessage message="no_access_work_schedules" />;

  return (
    <MultipleTabsLayout
      basePath="/app/work-schedules"
      tabs={tabs}
      tabIndex={tabIndex}
      title={t('work_schedules_and_holidays')}
      action={
        hasCreatePermission(PermissionEntity.SETTINGS)
          ? () => setOpenAddModal(true)
          : null
      }
      actionTitle={tabIndex === 0 ? t('work_schedule_add') : t('holiday_add')}
    >
      {tabIndex === 0 ? (
        <Schedules
          openModal={openAddModal}
          handleCloseModal={() => setOpenAddModal(false)}
        />
      ) : (
        <Holidays
          openModal={openAddModal}
          handleCloseModal={() => setOpenAddModal(false)}
        />
      )}
    </MultipleTabsLayout>
  );
}

export default WorkSchedules;
