/**
 * 댓글 입력 컴포넌트
 *
 * Instagram 스타일의 댓글 입력 필드
 * - 하단 고정
 * - 프로필 이미지
 * - TextInput with placeholder
 * - 게시 버튼 (입력 시에만 활성화)
 * - 답글 모드: "@닉네임" 표시 + 취소 버튼
 */

import React, { useState, useEffect, useRef } from 'react';
import { View, Text, TextInput, TouchableOpacity, Image, StyleSheet, KeyboardAvoidingView, Platform } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTranslation } from 'react-i18next';
import { theme } from '@/theme';
import { useAuthStore } from '@/stores/authStore';

interface CommentInputProps {
  onSubmit: (content: string, parentCommentId?: string) => void;
  replyTo?: { commentId: string; nickname: string } | null;
  onCancelReply?: () => void;
  placeholder?: string;
}

export const CommentInput: React.FC<CommentInputProps> = ({
  onSubmit,
  replyTo,
  onCancelReply,
  placeholder,
}) => {
  const { t } = useTranslation('interactions');
  const insets = useSafeAreaInsets();
  const profile = useAuthStore((state) => state.profile);
  const [content, setContent] = useState('');
  const inputRef = useRef<TextInput>(null);

  // 답글 모드일 때 자동으로 포커스
  useEffect(() => {
    if (replyTo) {
      inputRef.current?.focus();
    }
  }, [replyTo]);

  const handleSubmit = () => {
    const trimmedContent = content.trim();
    if (!trimmedContent) return;

    // 답글인 경우 parentCommentId 전달
    if (replyTo) {
      onSubmit(trimmedContent, replyTo.commentId);
    } else {
      onSubmit(trimmedContent);
    }

    // 입력 필드 초기화
    setContent('');
  };

  const isSubmitEnabled = content.trim().length > 0;

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      keyboardVerticalOffset={0}
    >
      <View style={[styles.container, { paddingBottom: insets.bottom || theme.spacing[4] }]}>
        {/* 답글 모드 표시 */}
        {replyTo && (
          <View style={styles.replyIndicator}>
            <Ionicons name="return-down-forward" size={14} color={theme.colors.text.secondary} />
            <View style={{ marginLeft: theme.spacing[2], flex: 1 }}>
              <Text style={styles.replyText}>{t('comment.replyWriting', { name: replyTo.nickname })}</Text>
            </View>
            {onCancelReply && (
              <TouchableOpacity onPress={onCancelReply} style={styles.cancelButton}>
                <Ionicons name="close-circle" size={18} color={theme.colors.text.tertiary} />
              </TouchableOpacity>
            )}
          </View>
        )}

        {/* 입력 필드 */}
        <View style={styles.inputWrapper}>
          {/* 프로필 이미지 */}
          {profile?.profileImageUrl ? (
            <Image
              source={{ uri: profile.profileImageUrl }}
              style={styles.profileImage}
            />
          ) : (
            <View style={[styles.profileImage, styles.profileImagePlaceholder]}>
              <Ionicons name="person" size={16} color={theme.colors.text.tertiary} />
            </View>
          )}

          {/* 텍스트 입력 */}
          <TextInput
            ref={inputRef}
            style={styles.textInput}
            placeholder={placeholder || t('comment.placeholder')}
            placeholderTextColor={theme.colors.text.tertiary}
            value={content}
            onChangeText={setContent}
            multiline
            maxLength={1000}
            returnKeyType="default"
            blurOnSubmit={false}
          />

          {/* 게시 버튼 */}
          <TouchableOpacity
            onPress={handleSubmit}
            disabled={!isSubmitEnabled}
            style={styles.submitButton}
          >
            <Ionicons
              name="send"
              size={20}
              color={isSubmitEnabled ? theme.colors.primary[500] : theme.colors.text.tertiary}
            />
          </TouchableOpacity>
        </View>
      </View>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: theme.colors.background.primary,
    borderTopWidth: 1,
    borderTopColor: theme.colors.border.light,
    paddingTop: theme.spacing[3],
    paddingHorizontal: theme.spacing[4],
  },
  replyIndicator: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: theme.spacing[2],
    paddingHorizontal: theme.spacing[3],
    backgroundColor: theme.colors.background.secondary,
    borderRadius: theme.borderRadius.base,
    marginBottom: theme.spacing[3],
  },
  replyText: {
    fontSize: theme.typography.fontSize.xs,
    color: theme.colors.text.secondary,
  },
  cancelButton: {
    padding: theme.spacing[1],
  },
  inputWrapper: {
    flexDirection: 'row',
    alignItems: 'flex-end',
  },
  profileImage: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: theme.colors.background.tertiary,
  },
  profileImagePlaceholder: {
    justifyContent: 'center',
    alignItems: 'center',
  },
  textInput: {
    flex: 1,
    marginLeft: theme.spacing[3],
    marginRight: theme.spacing[2],
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
    maxHeight: 100, // 최대 높이 제한
    paddingVertical: theme.spacing[2],
  },
  submitButton: {
    padding: theme.spacing[2],
  },
});
