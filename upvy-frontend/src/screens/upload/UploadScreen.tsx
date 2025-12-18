import React from 'react';
import { View, Text } from 'react-native';
import { useTranslation } from 'react-i18next';
import { createStyleSheet } from '@/utils/styles';

const useStyles = createStyleSheet((theme) => ({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: theme.colors.background.primary,
  },
  title: {
    fontSize: theme.typography.fontSize.xl,
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.primary,
  },
}));

export default function UploadScreen() {
  const styles = useStyles();
  const { t } = useTranslation('upload');

  return (
    <View style={styles.container}>
      <Text style={styles.title}>{t('title')}</Text>
    </View>
  );
}
