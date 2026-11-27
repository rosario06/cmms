import {
  Alert,
  Box,
  Button,
  Card,
  CircularProgress,
  Grid,
  Link,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography
} from '@mui/material';
import ReceiptLongTwoToneIcon from '@mui/icons-material/ReceiptLongTwoTone';
import { Link as RouterLink } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useContext, useEffect, useState } from 'react';
import WorkOrder from '../../../../models/owns/workOrder';
import { WorkOrderFinancials } from '../../../../models/owns/financial';
import { PermissionEntity } from '../../../../models/owns/role';
import { useDispatch } from '../../../../store';
import useAuth from '../../../../hooks/useAuth';
import { getWorkOrderFinancials } from '../../../../slices/financial';
import { createInvoiceFromWorkOrder } from '../../../../slices/invoice';
import { CompanySettingsContext } from '../../../../contexts/CompanySettingsContext';
import { CustomSnackBarContext } from '../../../../contexts/CustomSnackBarContext';
import { getMarginColor } from '../../CostCenters/utils';

interface FinancialSummaryProps {
  workOrder: WorkOrder;
  /** Changes whenever labor, parts or costs change so the summary reloads. */
  refreshKey?: number;
}

export default function FinancialSummary({
  workOrder,
  refreshKey = 0
}: FinancialSummaryProps) {
  const { t }: { t: any } = useTranslation();
  const dispatch = useDispatch();
  const { hasViewPermission, hasCreatePermission } = useAuth();
  const { getFormattedCurrency } = useContext(CompanySettingsContext);
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const canView = hasViewPermission(PermissionEntity.ANALYTICS);
  const canGenerate =
    canView &&
    hasCreatePermission(PermissionEntity.SETTINGS) &&
    workOrder.status === 'COMPLETE';
  const [financials, setFinancials] = useState<WorkOrderFinancials | null>(
    null
  );
  const [loading, setLoading] = useState<boolean>(false);
  const [failed, setFailed] = useState<boolean>(false);
  const [generating, setGenerating] = useState<boolean>(false);
  const [generated, setGenerated] = useState<boolean>(false);

  useEffect(() => {
    if (!canView) return undefined;
    let cancelled = false;
    setLoading(true);
    setFailed(false);
    dispatch(getWorkOrderFinancials(workOrder.id))
      .then((result: WorkOrderFinancials) => {
        if (!cancelled) setFinancials(result);
      })
      .catch((err) => {
        if (cancelled) return;
        setFinancials(null);
        // A 403 means the user cannot see financials: stay silent.
        setFailed(err?.status !== 403);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [canView, workOrder.id, refreshKey]);

  useEffect(() => {
    setGenerated(false);
  }, [workOrder.id]);

  const handleGenerate = async () => {
    setGenerating(true);
    try {
      await dispatch(createInvoiceFromWorkOrder(workOrder.id));
      setGenerated(true);
      showSnackBar(t('invoice_generate_success'), 'success');
    } catch (err) {
      // The API client does not preserve the backend message (409 included).
      showSnackBar(t('invoice_generate_failure'), 'error');
    } finally {
      setGenerating(false);
    }
  };

  if (!canView) return null;

  const money = (value: number | undefined) => getFormattedCurrency(value ?? 0);

  const rows = financials
    ? [
        {
          label: t('financial_labor'),
          internal: financials.laborInternal,
          billable: financials.laborBillable
        },
        {
          label: t('financial_parts'),
          internal: financials.partsInternal,
          billable: financials.partsBillable
        },
        {
          label: t('financial_additional'),
          internal: financials.additionalInternal,
          billable: financials.additionalBillable
        }
      ]
    : [];

  return (
    <Box sx={{ mt: 2 }}>
      <Typography sx={{ mb: 1 }} variant="h3">
        {t('financial_summary_title')}
      </Typography>
      {loading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
          <CircularProgress size={24} />
        </Box>
      )}
      {!loading && failed && (
        <Typography variant="body2" color="error">
          {t('financial_summary_failure')}
        </Typography>
      )}
      {!loading && financials && (
        <Card variant="outlined" sx={{ p: 2 }}>
          <Grid container spacing={2}>
            <Grid item xs={12} sm={4}>
              <Typography variant="subtitle2">
                {t('financial_internal_cost')}
              </Typography>
              <Typography variant="h4">
                {money(financials.totalInternalCost)}
              </Typography>
            </Grid>
            <Grid item xs={12} sm={4}>
              <Typography variant="subtitle2">
                {t('financial_total_billable')}
              </Typography>
              <Typography variant="h4">
                {money(financials.totalBillableAmount)}
              </Typography>
            </Grid>
            <Grid item xs={12} sm={4}>
              <Typography variant="subtitle2">
                {t('financial_net_margin')}
              </Typography>
              <Typography
                variant="h4"
                sx={{ color: getMarginColor(financials.netMarginAmount) }}
              >
                {`${money(financials.netMarginAmount)} (${(
                  financials.marginPercentage ?? 0
                ).toFixed(1)}%)`}
              </Typography>
            </Grid>
          </Grid>
          <Table size="small" sx={{ mt: 2 }}>
            <TableHead>
              <TableRow>
                <TableCell>{t('financial_breakdown')}</TableCell>
                <TableCell align="right">
                  {t('financial_internal_cost')}
                </TableCell>
                <TableCell align="right">{t('financial_billable')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={row.label}>
                  <TableCell>{row.label}</TableCell>
                  <TableCell align="right">{money(row.internal)}</TableCell>
                  <TableCell align="right">{money(row.billable)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <Typography variant="body2" sx={{ mt: 1 }}>
            {`${t('financial_covered_hours')}: ${(
              financials.contractCoveredHours ?? 0
            ).toFixed(2)} h`}
          </Typography>
        </Card>
      )}
      {canGenerate && (
        <Box sx={{ mt: 2 }}>
          <Button
            variant="contained"
            disabled={generating || generated}
            startIcon={
              generating ? (
                <CircularProgress size={16} />
              ) : (
                <ReceiptLongTwoToneIcon />
              )
            }
            onClick={handleGenerate}
          >
            {t('invoice_generate')}
          </Button>
          {generated && (
            <Alert severity="success" sx={{ mt: 1 }}>
              {`${t('invoice_generate_success')}. `}
              <Link component={RouterLink} to="/app/invoices">
                {t('invoice_view_invoices')}
              </Link>
            </Alert>
          )}
        </Box>
      )}
    </Box>
  );
}
