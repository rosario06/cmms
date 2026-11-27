import {
  Box,
  Button,
  CircularProgress,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useContext, useEffect, useState } from 'react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from 'recharts';
import { useDispatch, useSelector } from '../../../store';
import { getCustomersMini } from '../../../slices/customer';
import { getProfitabilityReport } from '../../../slices/financial';
import { ProfitabilityReport } from '../../../models/owns/financial';
import { CustomSnackBarContext } from '../../../contexts/CustomSnackBarContext';
import { CompanySettingsContext } from '../../../contexts/CompanySettingsContext';
import { getErrorMessage } from '../../../utils/api';
import { getMarginColor } from './utils';

const toInputDate = (date: Date): string => {
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
};

const getDefaultStart = (): string => {
  const date = new Date();
  date.setDate(date.getDate() - 90);
  return toInputDate(date);
};

const Profitability = () => {
  const { t }: { t: any } = useTranslation();
  const dispatch = useDispatch();
  const { customersMini } = useSelector((state) => state.customers);
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const { getFormattedCurrency } = useContext(CompanySettingsContext);
  const [startDate, setStartDate] = useState<string>(getDefaultStart());
  const [endDate, setEndDate] = useState<string>(toInputDate(new Date()));
  const [customerId, setCustomerId] = useState<string>('');
  const [report, setReport] = useState<ProfitabilityReport | null>(null);
  const [loading, setLoading] = useState<boolean>(false);

  const loadReport = () => {
    const start = new Date(`${startDate}T00:00:00`);
    const end = new Date(`${endDate}T23:59:59`);
    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return;
    if (end < start) {
      showSnackBar(t('financial_invalid_range'), 'error');
      return;
    }
    setLoading(true);
    dispatch(
      getProfitabilityReport(
        start,
        end,
        customerId ? Number(customerId) : null
      )
    )
      .then((result: ProfitabilityReport) => setReport(result))
      .catch((err) =>
        showSnackBar(getErrorMessage(err, t('financial_load_failure')), 'error')
      )
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    dispatch(getCustomersMini()).catch(() => undefined);
    loadReport();
  }, []);

  const byCustomer = report?.byCustomer ?? [];
  const byWorkOrder = report?.byWorkOrder ?? [];
  const chartData = byCustomer.map((row) => ({
    name: row.customerName ?? '-',
    billable: row.billable,
    internalCost: row.internalCost
  }));

  const renderMargin = (margin: number, percent: number) => (
    <>
      <TableCell align="right" sx={{ color: getMarginColor(margin) }}>
        {getFormattedCurrency(margin ?? 0)}
      </TableCell>
      <TableCell align="right" sx={{ color: getMarginColor(margin) }}>
        {`${(percent ?? 0).toFixed(1)}%`}
      </TableCell>
    </>
  );

  return (
    <Box sx={{ p: 3 }}>
      <Stack direction="row" spacing={2} flexWrap="wrap" sx={{ mb: 3 }}>
        <TextField
          size="small"
          type="date"
          label={t('financial_date_from')}
          value={startDate}
          onChange={(event) => setStartDate(event.target.value)}
          InputLabelProps={{ shrink: true }}
        />
        <TextField
          size="small"
          type="date"
          label={t('financial_date_to')}
          value={endDate}
          onChange={(event) => setEndDate(event.target.value)}
          InputLabelProps={{ shrink: true }}
        />
        <TextField
          select
          size="small"
          sx={{ minWidth: 220 }}
          label={t('contracts_customer')}
          value={customerId}
          onChange={(event) => setCustomerId(event.target.value)}
        >
          <MenuItem value="">{t('financial_all_customers')}</MenuItem>
          {customersMini.map((customer) => (
            <MenuItem key={customer.id} value={String(customer.id)}>
              {customer.name}
            </MenuItem>
          ))}
        </TextField>
        <Button variant="contained" onClick={loadReport} disabled={loading}>
          {t('financial_apply')}
        </Button>
      </Stack>
      {loading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
          <CircularProgress />
        </Box>
      )}
      {!loading && report && byCustomer.length === 0 && (
        <Typography>{t('financial_no_data')}</Typography>
      )}
      {!loading && byCustomer.length > 0 && (
        <>
          <Typography variant="h5" sx={{ mb: 1 }}>
            {t('financial_chart_title')}
          </Typography>
          <Box sx={{ width: '100%', height: 280, mb: 3 }}>
            <ResponsiveContainer>
              <BarChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="name" />
                <YAxis />
                <Tooltip
                  formatter={(value) => getFormattedCurrency(Number(value))}
                />
                <Legend />
                <Bar
                  dataKey="billable"
                  name={t('financial_billable')}
                  fill="#2e7d32"
                />
                <Bar
                  dataKey="internalCost"
                  name={t('financial_internal_cost')}
                  fill="#f9a825"
                />
              </BarChart>
            </ResponsiveContainer>
          </Box>
          <Typography variant="h5" sx={{ mb: 1 }}>
            {t('financial_by_customer')}
          </Typography>
          <TableContainer sx={{ mb: 3 }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>{t('contracts_customer')}</TableCell>
                  <TableCell align="right">
                    {t('financial_work_orders_count')}
                  </TableCell>
                  <TableCell align="right">
                    {t('financial_internal_cost')}
                  </TableCell>
                  <TableCell align="right">{t('financial_billable')}</TableCell>
                  <TableCell align="right">
                    {t('financial_net_margin')}
                  </TableCell>
                  <TableCell align="right">
                    {t('financial_margin_percent')}
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {byCustomer.map((row) => (
                  <TableRow key={`${row.customerId ?? 'none'}`}>
                    <TableCell>{row.customerName ?? '-'}</TableCell>
                    <TableCell align="right">{row.workOrderCount}</TableCell>
                    <TableCell align="right">
                      {getFormattedCurrency(row.internalCost ?? 0)}
                    </TableCell>
                    <TableCell align="right">
                      {getFormattedCurrency(row.billable ?? 0)}
                    </TableCell>
                    {renderMargin(row.netMargin, row.marginPercent)}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </>
      )}
      {!loading && byWorkOrder.length > 0 && (
        <>
          <Typography variant="h5" sx={{ mb: 1 }}>
            {t('financial_by_work_order')}
          </Typography>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>{t('financial_work_order')}</TableCell>
                  <TableCell>{t('contracts_customer')}</TableCell>
                  <TableCell align="right">
                    {t('financial_internal_cost')}
                  </TableCell>
                  <TableCell align="right">{t('financial_billable')}</TableCell>
                  <TableCell align="right">
                    {t('financial_net_margin')}
                  </TableCell>
                  <TableCell align="right">
                    {t('financial_margin_percent')}
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {byWorkOrder.map((row) => (
                  <TableRow key={row.workOrderId}>
                    <TableCell>{`#${row.workOrderId} ${row.title ?? ''}`}</TableCell>
                    <TableCell>{row.customerName ?? '-'}</TableCell>
                    <TableCell align="right">
                      {getFormattedCurrency(row.internalCost ?? 0)}
                    </TableCell>
                    <TableCell align="right">
                      {getFormattedCurrency(row.billable ?? 0)}
                    </TableCell>
                    {renderMargin(row.netMargin, row.marginPercent)}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </>
      )}
    </Box>
  );
};

export default Profitability;
