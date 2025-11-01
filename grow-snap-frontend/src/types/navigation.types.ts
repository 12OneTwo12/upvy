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

/**
 * Upload Stack Navigator Params
 */
export type UploadStackParamList = {
  UploadMain: undefined;
  VideoEdit: {
    uri: string;
    type: 'video';
    fileName: string;
    fileSize?: number;
  };
  PhotoEdit: {
    uris: string[];
    type: 'photo';
  };
  ContentMetadata: {
    contentId: string;
    contentType: 'VIDEO' | 'PHOTO';
    mediaInfo: {
      uri: string | string[];
      thumbnailUrl: string;
      duration?: number;
      width: number;
      height: number;
    };
  };
  ContentManagement: undefined;
};
