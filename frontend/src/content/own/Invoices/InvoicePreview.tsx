import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  Divider,
  Grid,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography
} from '@mui/material';
import PrintTwoToneIcon from '@mui/icons-material/PrintTwoTone';
import { useTranslation } from 'react-i18next';
import { useContext, useState } from 'react';
import { Invoice } from '../../../models/owns/invoice';
import { useDispatch } from '../../../store';
import { getInvoicePrintHtml } from '../../../slices/invoice';
import useAuth from '../../../hooks/useAuth';
import { CustomSnackBarContext } from '../../../contexts/CustomSnackBarContext';
import { CompanySettingsContext } from '../../../contexts/CompanySettingsContext';
import { getBillingErrorMessage } from '../../../utils/billingErrors';
import { formatInvoiceAmount, getInvoiceStatusColor } from './utils';

interface InvoicePreviewProps {
  invoice: Invoice | null;
  open: boolean;
  onClose: () => void;
}

const InvoicePreview = ({ invoice, open, onClose }: InvoicePreviewProps) => {
  const { t }: { t: any } = useTranslation();
  const dispatch = useDispatch();
  const { company } = useAuth();
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const { getFormattedDate, getFormattedCurrency } = useContext(
    CompanySettingsContext
  );
  const [printing, setPrinting] = useState<boolean>(false);

  const money = (amount: number) =>
    formatInvoiceAmount(amount, invoice?.currency, getFormattedCurrency);

  // The print endpoint needs the JWT, so the HTML is downloaded with the
  // authenticated client and printed from a hidden, script-less iframe.
  const handlePrint = async () => {
    if (!invoice) return;
    setPrinting(true);
    try {
      const html: string = await dispatch(getInvoicePrintHtml(invoice.id));
      const iframe = document.createElement('iframe');
      iframe.style.position = 'fixed';
      iframe.style.right = '0';
      iframe.style.bottom = '0';
      iframe.style.width = '0';
      iframe.style.height = '0';
      iframe.style.border = '0';
      iframe.setAttribute('sandbox', 'allow-same-origin allow-modals');
      iframe.onload = () => {
        iframe.contentWindow?.focus();
        iframe.contentWindow?.print();
        setTimeout(() => {
          if (iframe.parentNode) iframe.parentNode.removeChild(iframe);
        }, 60000);
      };
      iframe.srcdoc = html;
      document.body.appendChild(iframe);
    } catch (err) {
      showSnackBar(
        getBillingErrorMessage(
          err,
          'invoice_print',
          t,
          t('invoice_print_failure')
        ),
        'error'
      );
    } finally {
      setPrinting(false);
    }
  };

  return (
    <Dialog fullWidth maxWidth="md" open={open} onClose={onClose}>
      {invoice && (
        <>
          <DialogContent sx={{ p: 4 }}>
            <Grid container justifyContent="space-between" spacing={2}>
              <Grid item xs={12} sm={6}>
                <Typography variant="h3">{company?.name}</Typography>
                {company?.address && (
                  <Typography variant="body2">{company.address}</Typography>
                )}
                {company?.phone && (
                  <Typography variant="body2">{company.phone}</Typography>
                )}
              </Grid>
              <Grid item xs={12} sm={6} sx={{ textAlign: { sm: 'right' } }}>
                <Typography variant="h3">{t('invoice_title')}</Typography>
                <Typography variant="h5">{invoice.invoiceNumber}</Typography>
                <Chip
                  size="small"
                  sx={{ mt: 1 }}
                  color={getInvoiceStatusColor(invoice.status)}
                  label={t(`invoice_status_${invoice.status}`)}
                />
              </Grid>
            </Grid>
            <Divider sx={{ my: 3 }} />
            <Grid container spacing={2}>
              <Grid item xs={12} sm={6}>
                <Typography variant="subtitle2">{t('invoice_bill_to')}</Typography>
                <Typography variant="body1">
                  {invoice.customerBillingName ?? invoice.customer?.name ?? '-'}
                </Typography>
                {invoice.workOrderId && (
                  <Typography variant="body2">
                    {`${t('invoice_work_order')}: #${invoice.workOrderId} ${
                      invoice.workOrderTitle ?? ''
                    }`}
                  </Typography>
                )}
                {invoice.serviceContractId && (
                  <Typography variant="body2">
                    {`${t('invoice_contract')}: ${
                      invoice.contractNumber ?? invoice.serviceContractId
                    }`}
                  </Typography>
                )}
              </Grid>
              <Grid item xs={12} sm={6} sx={{ textAlign: { sm: 'right' } }}>
                <Typography variant="body2">
                  {`${t('invoice_issue_date')}: ${
                    invoice.issueDate
                      ? getFormattedDate(invoice.issueDate, true)
                      : '-'
                  }`}
                </Typography>
                <Typography variant="body2">
                  {`${t('invoice_due_date')}: ${
                    invoice.dueDate
                      ? getFormattedDate(invoice.dueDate, true)
                      : '-'
                  }`}
                </Typography>
                {invoice.paidAt && (
                  <Typography variant="body2">
                    {`${t('invoice_paid_at')}: ${getFormattedDate(
                      invoice.paidAt,
                      true
                    )}`}
                  </Typography>
                )}
              </Grid>
            </Grid>
            <Table size="small" sx={{ mt: 3 }}>
              <TableHead>
                <TableRow>
                  <TableCell>{t('invoice_line_description')}</TableCell>
                  <TableCell>{t('invoice_line_type')}</TableCell>
                  <TableCell align="right">{t('invoice_line_quantity')}</TableCell>
                  <TableCell align="right">
                    {t('invoice_line_unit_price')}
                  </TableCell>
                  <TableCell align="right">{t('invoice_line_total')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {(invoice.lines ?? []).map((line) => (
                  <TableRow key={line.id}>
                    <TableCell>{line.description}</TableCell>
                    <TableCell>{t(`invoice_item_type_${line.itemType}`)}</TableCell>
                    <TableCell align="right">{line.quantity}</TableCell>
                    <TableCell align="right">{money(line.unitPrice)}</TableCell>
                    <TableCell align="right">{money(line.lineTotal)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Box sx={{ mt: 3, ml: 'auto', maxWidth: 320 }}>
              <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                <Typography>{t('invoice_subtotal')}</Typography>
                <Typography>{money(invoice.subtotal)}</Typography>
              </Box>
              <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                <Typography>
                  {`${t('invoice_tax')} (${invoice.taxRatePercent ?? 0}%)`}
                </Typography>
                <Typography>{money(invoice.taxAmount)}</Typography>
              </Box>
              <Divider sx={{ my: 1 }} />
              <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                <Typography variant="h4">{t('invoice_total')}</Typography>
                <Typography variant="h4">{money(invoice.totalAmount)}</Typography>
              </Box>
            </Box>
            {invoice.notes && (
              <Box sx={{ mt: 3 }}>
                <Typography variant="subtitle2">{t('notes')}</Typography>
                <Typography variant="body2">{invoice.notes}</Typography>
              </Box>
            )}
          </DialogContent>
          <DialogActions sx={{ p: 2 }}>
            <Button onClick={onClose}>{t('close')}</Button>
            <Button
              variant="contained"
              disabled={printing}
              startIcon={
                printing ? (
                  <CircularProgress size={16} />
                ) : (
                  <PrintTwoToneIcon />
                )
              }
              onClick={handlePrint}
            >
              {t('invoice_print')}
            </Button>
          </DialogActions>
        </>
      )}
    </Dialog>
  );
};

export default InvoicePreview;
