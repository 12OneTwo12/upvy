import { NavigatorScreenParams } from '@react-navigation/native';
import type { Category } from './content.types';

/**
 * Root Stack Navigator Params
 */
export type RootStackParamList = {
  Auth: NavigatorScreenParams<AuthStackParamList>;
  ProfileSetup: undefined;
  Main: NavigatorScreenParams<MainTabParamList>;
  EditProfile: undefined;
  Settings: undefined;
  LanguageSelector: undefined;
  TermsOfService: undefined;
  PrivacyPolicy: undefined;
  HelpSupport: undefined;
  BlockManagement: undefined;
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
  Explore: NavigatorScreenParams<ExploreStackParamList>;
  Feed: NavigatorScreenParams<FeedStackParamList>;
  Search: NavigatorScreenParams<SearchStackParamList>;
  Upload: NavigatorScreenParams<UploadStackParamList>;
  Profile: NavigatorScreenParams<ProfileStackParamList>;
};

/**
 * Media Asset (갤러리에서 선택한 미디어)
 */
export interface MediaAsset {
  id: string;
  uri: string;
  mediaType: 'photo' | 'video';
  duration: number;
  width: number;
  height: number;
  filename: string;
}

/**
 * Upload Stack Navigator Params
 */
export type UploadStackParamList = {
  UploadMain: undefined;
  VideoEdit: {
    asset: MediaAsset;
    type: 'video';
  };
  PhotoEdit: {
    assets: MediaAsset[];
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

/**
 * Explore Stack Navigator Params
 */
export type ExploreStackParamList = {
  ExploreMain: undefined;
  CategoryFeed: { category: Category };
  ContentViewer: { contentId: string };
  UserProfile: { userId: string };
  FollowList: { userId: string; initialTab?: 'followers' | 'following' };
};

/**
 * Feed Stack Navigator Params
 */
export type FeedStackParamList = {
  FeedMain: undefined;
  ContentViewer: { contentId: string };
  UserProfile: { userId: string };
  FollowList: { userId: string; initialTab?: 'followers' | 'following' };
};

/**
 * Search Stack Navigator Params
 */
export type SearchStackParamList = {
  SearchMain: undefined;
  ContentViewer: { contentId: string };
  UserProfile: { userId: string };
  FollowList: { userId: string; initialTab?: 'followers' | 'following' };
};

/**
 * Profile Stack Navigator Params
 */
export type ProfileStackParamList = {
  ProfileMain: undefined;
  ContentViewer: { contentId: string };
  UserProfile: { userId: string };
  FollowList: { userId: string; initialTab?: 'followers' | 'following' };
};
