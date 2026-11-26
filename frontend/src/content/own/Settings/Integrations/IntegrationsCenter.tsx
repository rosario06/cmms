import {
  Box,
  Button,
  Card,
  CardActions,
  CardContent,
  Chip,
  Grid,
  Typography
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { ReactElement, useEffect, useState } from 'react';
import SensorsIcon from '@mui/icons-material/Sensors';
import MapIcon from '@mui/icons-material/Map';
import PaymentsIcon from '@mui/icons-material/Payments';
import WhatsAppIcon from '@mui/icons-material/WhatsApp';
import { useDispatch, useSelector } from '../../../../store';
import { getIntegrations } from '../../../../slices/integration';
import IotConfigModal from './IotConfigModal';
import MapsConfigModal from './MapsConfigModal';
import PaymentsConfigModal from './PaymentsConfigModal';
import NotificationsConfigModal from './NotificationsConfigModal';

interface IntegrationCard {
  key: string;
  title: string;
  description: string;
  icon: ReactElement;
  active: boolean;
}

function IntegrationsCenter() {
  const { t }: { t: any } = useTranslation();
  const dispatch = useDispatch();
  const { iot, maps, payments, notifications } = useSelector((state) => state.integrations);
  const [openModal, setOpenModal] = useState<string | null>(null);

  useEffect(() => {
    dispatch(getIntegrations());
  }, []);

  const closeModal = () => setOpenModal(null);

  const cards: IntegrationCard[] = [
    {
      key: 'iot',
      title: t('integration_iot_title'),
      description: t('integration_iot_description'),
      icon: <SensorsIcon fontSize="large" />,
      active: !!iot?.enabled
    },
    {
      key: 'maps',
      title: t('integration_maps_title'),
      description: t('integration_maps_description'),
      icon: <MapIcon fontSize="large" />,
      active: !!maps?.enabled
    },
    {
      key: 'payments',
      title: t('integration_payments_title'),
      description: t('integration_payments_description'),
      icon: <PaymentsIcon fontSize="large" />,
      active: !!payments?.enabled
    },
    {
      key: 'whatsapp',
      title: t('integration_whatsapp_title'),
      description: t('integration_whatsapp_description'),
      icon: <WhatsAppIcon fontSize="large" />,
      active: !!notifications?.whatsappEnabled
    }
  ];

  return (
    <Box pt={3}>
      <Typography variant="h3" gutterBottom>
        {t('integrations_center')}
      </Typography>
      <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 3 }}>
        {t('integrations_center_description')}
      </Typography>
      <Grid container spacing={3}>
        {cards.map((card) => (
          <Grid item xs={12} sm={6} lg={3} key={card.key}>
            <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
              <CardContent sx={{ flexGrow: 1 }}>
                <Box
                  display="flex"
                  justifyContent="space-between"
                  alignItems="center"
                  mb={2}
                >
                  <Box color="primary.main">{card.icon}</Box>
                  <Chip
                    size="small"
                    color={card.active ? 'success' : 'default'}
                    label={t(
                      card.active ? 'integration_active' : 'integration_inactive'
                    )}
                  />
                </Box>
                <Typography variant="h5" gutterBottom>
                  {card.title}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {card.description}
                </Typography>
              </CardContent>
              <CardActions sx={{ p: 2 }}>
                <Button
                  variant="contained"
                  onClick={() => setOpenModal(card.key)}
                >
                  {t('integration_configure')}
                </Button>
              </CardActions>
            </Card>
          </Grid>
        ))}
      </Grid>
      <IotConfigModal open={openModal === 'iot'} onClose={closeModal} />
      <MapsConfigModal open={openModal === 'maps'} onClose={closeModal} />
      <PaymentsConfigModal
        open={openModal === 'payments'}
        onClose={closeModal}
      />
      <NotificationsConfigModal
        open={openModal === 'whatsapp'}
        onClose={closeModal}
      />
    </Box>
  );
}

export default IntegrationsCenter;
