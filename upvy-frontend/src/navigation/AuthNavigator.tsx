import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { AuthStackParamList } from '@/types/navigation.types';
import LoginScreen from '@/screens/auth/LoginScreen';
import TermsOfServiceScreen from '@/screens/settings/TermsOfServiceScreen';
import PrivacyPolicyScreen from '@/screens/settings/PrivacyPolicyScreen';

const Stack = createNativeStackNavigator<AuthStackParamList>();

/**
 * Auth Navigator
 * 로그인 화면 및 약관/정책 화면을 포함합니다.
 */
export default function AuthNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="Login" component={LoginScreen} />
      <Stack.Screen name="TermsOfService" component={TermsOfServiceScreen} />
      <Stack.Screen name="PrivacyPolicy" component={PrivacyPolicyScreen} />
    </Stack.Navigator>
  );
}
