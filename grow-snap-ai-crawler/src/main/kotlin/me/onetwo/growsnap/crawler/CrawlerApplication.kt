package me.onetwo.growsnap.crawler

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * GrowSnap AI Crawler Application
 *
 * YouTube CC 라이선스 콘텐츠를 크롤링하여 AI로 분석하고
 * 숏폼 콘텐츠를 자동 생성하는 Spring Batch 기반 애플리케이션
 */
@SpringBootApplication
@EnableScheduling
class CrawlerApplication

fun main(args: Array<String>) {
    runApplication<CrawlerApplication>(*args)
}
