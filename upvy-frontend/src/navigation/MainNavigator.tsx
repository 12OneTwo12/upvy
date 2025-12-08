import React from 'react';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { MainTabParamList } from '@/types/navigation.types';
import { theme } from '@/theme';
import ExploreNavigator from '@/navigation/ExploreNavigator';
import FeedNavigator from '@/navigation/FeedNavigator';
import SearchNavigator from '@/navigation/SearchNavigator';
import UploadNavigator from '@/navigation/UploadNavigator';
import ProfileNavigator from '@/navigation/ProfileNavigator';

const Tab = createBottomTabNavigator<MainTabParamList>();

/**
 * Main Tab Navigator
 * 하단 탭 네비게이션입니다.
 */
export default function MainNavigator() {
  const { t } = useTranslation('common');

  return (
    <Tab.Navigator
      initialRouteName="Feed"
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarStyle: {
          height: 68,
          paddingBottom: 16,
        },
        tabBarIcon: ({ focused, color, size }) => {
          let iconName: keyof typeof Ionicons.glyphMap;

          if (route.name === 'Explore') {
            iconName = focused ? 'compass' : 'compass-outline';
          } else if (route.name === 'Feed') {
            iconName = focused ? 'home' : 'home-outline';
          } else if (route.name === 'Search') {
            iconName = focused ? 'search' : 'search-outline';
          } else if (route.name === 'Upload') {
            iconName = focused ? 'add-circle' : 'add-circle-outline';
          } else if (route.name === 'Profile') {
            iconName = focused ? 'person' : 'person-outline';
          } else {
            iconName = 'help-circle-outline';
          }

          return <Ionicons name={iconName} size={size} color={color} />;
        },
        tabBarActiveTintColor: theme.colors.primary[500],
        tabBarInactiveTintColor: theme.colors.gray[400],
      })}
    >
      <Tab.Screen
        name="Explore"
        component={ExploreNavigator}
        options={{ tabBarLabel: t('navigation.explore') }}
      />
      <Tab.Screen
        name="Search"
        component={SearchNavigator}
        options={{ tabBarLabel: t('navigation.search') }}
        listeners={({ navigation }) => ({
          tabPress: (e) => {
            // 이미 Search 탭에 있을 때 탭을 다시 누르면 스택 리셋
            const state = navigation.getState();
            const currentRoute = state.routes[state.index];
            if (currentRoute.name === 'Search') {
              // 스택의 첫 화면으로 이동 (SearchMain)
              navigation.navigate('Search', { screen: 'SearchMain' });
            }
          },
        })}
      />
      <Tab.Screen
        name="Feed"
        component={FeedNavigator}
        options={{ tabBarLabel: t('navigation.feed') }}
      />
      <Tab.Screen
        name="Upload"
        component={UploadNavigator}
        options={{ tabBarLabel: t('navigation.upload') }}
      />
      <Tab.Screen
        name="Profile"
        component={ProfileNavigator}
        options={{ tabBarLabel: t('navigation.profile') }}
      />
    </Tab.Navigator>
  );
}
