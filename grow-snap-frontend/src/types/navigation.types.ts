import { NavigatorScreenParams } from '@react-navigation/native';

/**
 * Root Stack Navigator Params
 */
export type RootStackParamList = {
  Auth: NavigatorScreenParams<AuthStackParamList>;
  ProfileSetup: undefined;
  Main: NavigatorScreenParams<MainTabParamList>;
  EditProfile: undefined;
  UserProfile: { userId: string };
  FollowList: { userId: string; initialTab?: 'followers' | 'following' };
};

/**
 * Auth Stack Navigator Params
 */
export type AuthStackParamList = {
  Login: undefined;
};

/**
 * Main Tab Navigator Params
 */
export type MainTabParamList = {
  Feed: undefined;
  Search: undefined;
  Upload: undefined;
  Profile: undefined;
};
