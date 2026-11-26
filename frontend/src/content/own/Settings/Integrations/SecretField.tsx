import { Box, Button, TextField, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';

interface SecretFieldProps {
  label: string;
  hasValue: boolean;
  last4: string | null;
  value: string;
  cleared: boolean;
  disabled?: boolean;
  onChange: (value: string) => void;
  onClear: () => void;
  onUndoClear: () => void;
}

// Never displays the stored secret: only a mask with its last 4 characters.
function SecretField({
  label,
  hasValue,
  last4,
  value,
  cleared,
  disabled,
  onChange,
  onClear,
  onUndoClear
}: SecretFieldProps) {
  const { t }: { t: any } = useTranslation();
  const placeholder = hasValue && !cleared ? `••••••••${last4 ?? ''}` : '';

  return (
    <Box>
      <TextField
        label={label}
        type="password"
        autoComplete="new-password"
        value={value}
        placeholder={placeholder}
        helperText={hasValue && !cleared ? t('integration_secret_helper') : ''}
        onChange={(event) => onChange(event.target.value)}
        disabled={disabled}
        fullWidth
      />
      {cleared ? (
        <Typography variant="body2" color="error" mt={0.5}>
          {t('integration_secret_will_delete')}{' '}
          <Button size="small" onClick={onUndoClear} disabled={disabled}>
            {t('integration_secret_undo')}
          </Button>
        </Typography>
      ) : (
        hasValue && (
          <Button
            size="small"
            color="error"
            onClick={onClear}
            disabled={disabled}
          >
            {t('integration_secret_clear')}
          </Button>
        )
      )}
    </Box>
  );
}

export default SecretField;
