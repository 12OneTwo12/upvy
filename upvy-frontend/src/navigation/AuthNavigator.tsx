import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { AuthStackParamList } from '@/types/navigation.types';
import LoginScreen from '@/screens/auth/LoginScreen';

const Stack = createNativeStackNavigator<AuthStackParamList>();

/**
 * Auth Navigator
 * 로그인 화면을 포함합니다.
 */
export default function AuthNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="Login" component={LoginScreen} />
    </Stack.Navigator>
  );
}
