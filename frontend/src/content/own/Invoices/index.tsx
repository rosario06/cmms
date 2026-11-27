import {
  Box,
  Button,
  Card,
  Chip,
  CircularProgress,
  debounce,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  IconButton,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from '@mui/material';
import VisibilityTwoToneIcon from '@mui/icons-material/VisibilityTwoTone';
import EditTwoToneIcon from '@mui/icons-material/EditTwoTone';
import SendTwoToneIcon from '@mui/icons-material/SendTwoTone';
import PaidTwoToneIcon from '@mui/icons-material/PaidTwoTone';
import CancelTwoToneIcon from '@mui/icons-material/CancelTwoTone';
import DeleteTwoToneIcon from '@mui/icons-material/DeleteTwoTone';
import { Helmet } from 'react-helmet-async';
import { useTranslation } from 'react-i18next';
import { useContext, useEffect, useMemo, useState } from 'react';
import PermissionErrorMessage from '../components/PermissionErrorMessage';
import { TitleContext } from '../../../contexts/TitleContext';
import { CustomSnackBarContext } from '../../../contexts/CustomSnackBarContext';
import { CompanySettingsContext } from '../../../contexts/CompanySettingsContext';
import useAuth from '../../../hooks/useAuth';
import { useDispatch, useSelector } from '../../../store';
import { PermissionEntity } from '../../../models/owns/role';
import {
  INVOICE_STATUSES,
  Invoice,
  InvoiceStatus
} from '../../../models/owns/invoice';
import {
  cancelInvoice,
  deleteInvoice,
  editInvoice,
  getInvoices,
  getInvoiceSummary,
  issueInvoice,
  markInvoicePaid
} from '../../../slices/invoice';
import { getCustomersMini } from '../../../slices/customer';
import { getErrorMessage } from '../../../utils/api';
import {
  BillingErrorKind,
  getBillingErrorMessage,
  isInvoiceStateError
} from '../../../utils/billingErrors';
import InvoicePreview from './InvoicePreview';
import {
  formatInvoiceAmount,
  getInvoiceStatusColor,
  inputDateToIso,
  isoToInputDate
} from './utils';

type InvoiceAction = 'issue' | 'markPaid' | 'cancel' | 'delete';

const CONFIRM_KEYS: Record<InvoiceAction, string> = {
  issue: 'invoice_confirm_issue',
  markPaid: 'invoice_confirm_mark_paid',
  cancel: 'invoice_confirm_cancel',
  delete: 'invoice_confirm_delete'
};

const SUCCESS_KEYS: Record<InvoiceAction, string> = {
  issue: 'invoice_issue_success',
  markPaid: 'invoice_mark_paid_success',
  cancel: 'invoice_cancel_success',
  delete: 'invoice_delete_success'
};

const ACTION_ERROR_KINDS: Record<InvoiceAction, BillingErrorKind> = {
  issue: 'invoice_issue',
  markPaid: 'invoice_pay',
  cancel: 'invoice_cancel',
  delete: 'invoice_delete'
};

function Invoices() {
  const { t }: { t: any } = useTranslation();
  const dispatch = useDispatch();
  const { setTitle } = useContext(TitleContext);
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const { getFormattedDate, getFormattedCurrency } = useContext(
    CompanySettingsContext
  );
  const { hasViewPermission, hasCreatePermission } = useAuth();
  const canView = hasViewPermission(PermissionEntity.ANALYTICS);
  const canWrite = hasCreatePermission(PermissionEntity.SETTINGS);
  const { invoices, summary, loadingGet } = useSelector(
    (state) => state.invoices
  );
  const { customersMini } = useSelector((state) => state.customers);

  const [status, setStatus] = useState<string>('');
  const [customerId, setCustomerId] = useState<string>('');
  const [text, setText] = useState<string>('');
  const [pageNum, setPageNum] = useState<number>(0);
  const [pageSize, setPageSize] = useState<number>(10);
  const [previewInvoice, setPreviewInvoice] = useState<Invoice | null>(null);
  const [pendingAction, setPendingAction] = useState<{
    action: InvoiceAction;
    invoice: Invoice;
  } | null>(null);
  const [editingInvoice, setEditingInvoice] = useState<Invoice | null>(null);
  const [editNotes, setEditNotes] = useState<string>('');
  const [editDueDate, setEditDueDate] = useState<string>('');
  const [editTax, setEditTax] = useState<string>('');
  const [saving, setSaving] = useState<boolean>(false);

  const debouncedSetText = useMemo(
    () =>
      debounce((value: string) => {
        setText(value);
        setPageNum(0);
      }, 800),
    []
  );

  useEffect(() => {
    setTitle(t('invoice_billing'));
  }, []);

  useEffect(() => {
    if (!canView) return;
    dispatch(getCustomersMini()).catch(() => undefined);
  }, [canView]);

  const reloadSummary = () => {
    dispatch(
      getInvoiceSummary(customerId ? Number(customerId) : null)
    ).catch((err) =>
      showSnackBar(getErrorMessage(err, t('invoice_load_failure')), 'error')
    );
  };

  const reloadInvoices = () => {
    dispatch(
      getInvoices({
        status: status ? (status as InvoiceStatus) : null,
        customerId: customerId ? Number(customerId) : null,
        text: text || null,
        pageNum,
        pageSize,
        sortField: 'id',
        direction: 'DESC'
      })
    ).catch((err) =>
      showSnackBar(getErrorMessage(err, t('invoice_load_failure')), 'error')
    );
  };

  useEffect(() => {
    if (canView) reloadInvoices();
  }, [canView, status, customerId, text, pageNum, pageSize]);

  useEffect(() => {
    if (canView) reloadSummary();
  }, [canView, customerId]);

  const runAction = async () => {
    if (!pendingAction) return;
    const { action, invoice } = pendingAction;
    setPendingAction(null);
    try {
      if (action === 'issue') await dispatch(issueInvoice(invoice.id));
      if (action === 'markPaid') await dispatch(markInvoicePaid(invoice.id));
      if (action === 'cancel') await dispatch(cancelInvoice(invoice.id));
      if (action === 'delete') await dispatch(deleteInvoice(invoice.id));
      showSnackBar(t(SUCCESS_KEYS[action]), 'success');
      reloadSummary();
      if (action === 'delete') reloadInvoices();
    } catch (err) {
      showSnackBar(
        getBillingErrorMessage(
          err,
          ACTION_ERROR_KINDS[action],
          t,
          t('invoice_action_failure')
        ),
        'error'
      );
      // The state seen by the user is stale: show the real one.
      if (isInvoiceStateError(err)) {
        reloadInvoices();
        reloadSummary();
      }
    }
  };

  const openEdit = (invoice: Invoice) => {
    setEditingInvoice(invoice);
    setEditNotes(invoice.notes ?? '');
    setEditDueDate(isoToInputDate(invoice.dueDate));
    setEditTax(String(invoice.taxRatePercent ?? 0));
  };

  const taxValue = Number(editTax);
  const taxInvalid =
    editTax === '' ||
    Number.isNaN(taxValue) ||
    taxValue < 0 ||
    taxValue > 100;

  const saveEdit = async () => {
    if (!editingInvoice || taxInvalid) return;
    setSaving(true);
    try {
      await dispatch(
        editInvoice(editingInvoice.id, {
          notes: editNotes,
          dueDate: inputDateToIso(editDueDate),
          taxRatePercent: taxValue
        })
      );
      setEditingInvoice(null);
      showSnackBar(t('invoice_edit_success'), 'success');
      reloadSummary();
    } catch (err) {
      showSnackBar(
        getBillingErrorMessage(
          err,
          'invoice_edit',
          t,
          t('invoice_action_failure')
        ),
        'error'
      );
      if (isInvoiceStateError(err)) {
        // The invoice is no longer editable (or gone): close and refresh.
        setEditingInvoice(null);
        reloadInvoices();
        reloadSummary();
      }
    } finally {
      setSaving(false);
    }
  };

  if (!canView) return <PermissionErrorMessage message="no_access_invoices" />;

  const money = (amount: number | undefined) =>
    getFormattedCurrency(amount ?? 0);

  const kpis = [
    {
      label: t('invoice_total_invoiced'),
      value: money(summary?.totalInvoiced),
      color: 'text.primary'
    },
    {
      label: t('invoice_pending_collection'),
      value: money(summary?.pendingCollection),
      color: 'warning.main'
    },
    {
      label: t('invoice_overdue'),
      value: money(summary?.overdue),
      color: 'error.main'
    }
  ];

  const renderActions = (invoice: Invoice) => {
    const editable = invoice.status === 'DRAFT';
    const open =
      invoice.status === 'ISSUED' || invoice.status === 'OVERDUE';
    return (
      <Stack direction="row" justifyContent="flex-end">
        <Tooltip title={t('invoice_preview')}>
          <IconButton size="small" onClick={() => setPreviewInvoice(invoice)}>
            <VisibilityTwoToneIcon fontSize="small" />
          </IconButton>
        </Tooltip>
        {canWrite && editable && (
          <>
            <Tooltip title={t('invoice_edit')}>
              <IconButton size="small" onClick={() => openEdit(invoice)}>
                <EditTwoToneIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            <Tooltip title={t('invoice_issue')}>
              <IconButton
                size="small"
                onClick={() => setPendingAction({ action: 'issue', invoice })}
              >
                <SendTwoToneIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            <Tooltip title={t('invoice_delete')}>
              <IconButton
                size="small"
                onClick={() => setPendingAction({ action: 'delete', invoice })}
              >
                <DeleteTwoToneIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          </>
        )}
        {canWrite && open && (
          <>
            <Tooltip title={t('invoice_mark_paid')}>
              <IconButton
                size="small"
                onClick={() =>
                  setPendingAction({ action: 'markPaid', invoice })
                }
              >
                <PaidTwoToneIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            <Tooltip title={t('invoice_cancel')}>
              <IconButton
                size="small"
                onClick={() => setPendingAction({ action: 'cancel', invoice })}
              >
                <CancelTwoToneIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          </>
        )}
      </Stack>
    );
  };

  return (
    <Box sx={{ p: 3 }}>
      <Helmet>
        <title>{t('invoice_billing')}</title>
      </Helmet>
      <Typography variant="h3" sx={{ mb: 2 }}>
        {t('invoice_billing')}
      </Typography>
      <Grid container spacing={2} sx={{ mb: 3 }}>
        {kpis.map((kpi) => (
          <Grid item xs={12} sm={4} key={kpi.label}>
            <Card variant="outlined" sx={{ p: 2 }}>
              <Typography variant="subtitle2">{kpi.label}</Typography>
              <Typography variant="h3" sx={{ color: kpi.color }}>
                {kpi.value}
              </Typography>
            </Card>
          </Grid>
        ))}
      </Grid>
      <Card variant="outlined" sx={{ p: 2 }}>
        <Stack direction="row" spacing={2} flexWrap="wrap" sx={{ mb: 2 }}>
          <TextField
            size="small"
            label={t('search')}
            onChange={(event) => debouncedSetText(event.target.value)}
          />
          <TextField
            select
            size="small"
            sx={{ minWidth: 180 }}
            label={t('status')}
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              setPageNum(0);
            }}
          >
            <MenuItem value="">{t('invoice_all_statuses')}</MenuItem>
            {INVOICE_STATUSES.map((item) => (
              <MenuItem key={item} value={item}>
                {t(`invoice_status_${item}`)}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            size="small"
            sx={{ minWidth: 220 }}
            label={t('contracts_customer')}
            value={customerId}
            onChange={(event) => {
              setCustomerId(event.target.value);
              setPageNum(0);
            }}
          >
            <MenuItem value="">{t('financial_all_customers')}</MenuItem>
            {customersMini.map((customer) => (
              <MenuItem key={customer.id} value={String(customer.id)}>
                {customer.name}
              </MenuItem>
            ))}
          </TextField>
        </Stack>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t('invoice_number')}</TableCell>
                <TableCell>{t('contracts_customer')}</TableCell>
                <TableCell>{t('invoice_issue_date')}</TableCell>
                <TableCell>{t('invoice_due_date')}</TableCell>
                <TableCell>{t('status')}</TableCell>
                <TableCell align="right">{t('invoice_total')}</TableCell>
                <TableCell align="right">{t('actions')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loadingGet && (
                <TableRow>
                  <TableCell colSpan={7} align="center">
                    <CircularProgress size={24} />
                  </TableCell>
                </TableRow>
              )}
              {!loadingGet && invoices.content.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7} align="center">
                    {t('invoice_no_rows')}
                  </TableCell>
                </TableRow>
              )}
              {!loadingGet &&
                invoices.content.map((invoice) => (
                  <TableRow key={invoice.id} hover>
                    <TableCell sx={{ fontWeight: 'bold' }}>
                      {invoice.invoiceNumber}
                    </TableCell>
                    <TableCell>{invoice.customer?.name ?? '-'}</TableCell>
                    <TableCell>
                      {invoice.issueDate
                        ? getFormattedDate(invoice.issueDate, true)
                        : '-'}
                    </TableCell>
                    <TableCell>
                      {invoice.dueDate
                        ? getFormattedDate(invoice.dueDate, true)
                        : '-'}
                    </TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        color={getInvoiceStatusColor(invoice.status)}
                        label={t(`invoice_status_${invoice.status}`)}
                      />
                    </TableCell>
                    <TableCell align="right">
                      {formatInvoiceAmount(
                        invoice.totalAmount,
                        invoice.currency,
                        getFormattedCurrency
                      )}
                    </TableCell>
                    <TableCell align="right">{renderActions(invoice)}</TableCell>
                  </TableRow>
                ))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div"
          count={invoices.totalElements}
          page={pageNum}
          rowsPerPage={pageSize}
          rowsPerPageOptions={[10, 20, 50]}
          onPageChange={(_event, page) => setPageNum(page)}
          onRowsPerPageChange={(event) => {
            setPageSize(Number(event.target.value));
            setPageNum(0);
          }}
        />
      </Card>
      <InvoicePreview
        invoice={previewInvoice}
        open={!!previewInvoice}
        onClose={() => setPreviewInvoice(null)}
      />
      <Dialog open={!!pendingAction} onClose={() => setPendingAction(null)}>
        <DialogContent>
          <Typography>
            {pendingAction ? t(CONFIRM_KEYS[pendingAction.action]) : ''}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPendingAction(null)}>{t('cancel')}</Button>
          <Button
            variant="contained"
            color={
              pendingAction?.action === 'cancel' ||
              pendingAction?.action === 'delete'
                ? 'error'
                : 'primary'
            }
            onClick={runAction}
          >
            {t('invoice_confirm')}
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog
        fullWidth
        maxWidth="sm"
        open={!!editingInvoice}
        onClose={() => setEditingInvoice(null)}
      >
        <DialogTitle>{t('invoice_edit')}</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              type="date"
              label={t('invoice_due_date')}
              value={editDueDate}
              onChange={(event) => setEditDueDate(event.target.value)}
              InputLabelProps={{ shrink: true }}
            />
            <TextField
              type="number"
              label={t('invoice_tax_rate')}
              value={editTax}
              onChange={(event) => setEditTax(event.target.value)}
              error={taxInvalid}
              helperText={taxInvalid ? t('cost_center_invalid_percent') : ''}
            />
            <TextField
              multiline
              minRows={3}
              label={t('notes')}
              value={editNotes}
              onChange={(event) => setEditNotes(event.target.value)}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditingInvoice(null)}>{t('cancel')}</Button>
          <Button
            variant="contained"
            disabled={saving || taxInvalid}
            onClick={saveEdit}
          >
            {t('save')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

export default Invoices;
