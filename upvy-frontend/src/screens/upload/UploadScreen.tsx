import React from 'react';
import { View, Text } from 'react-native';
import { useTranslation } from 'react-i18next';

export default function UploadScreen() {
  const { t } = useTranslation('upload');

  return (
    <View className="flex-1 items-center justify-center bg-white">
      <Text className="text-xl font-bold">{t('title')}</Text>
    </View>
  );
}
