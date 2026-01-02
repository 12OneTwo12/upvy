package me.onetwo.upvy.crawler.domain.content

import org.springframework.data.jpa.repository.JpaRepository

/**
 * 퀴즈 보기 Repository
 *
 * quiz_options 테이블에 접근하기 위한 Repository입니다.
 */
interface QuizOptionRepository : JpaRepository<QuizOption, String>
