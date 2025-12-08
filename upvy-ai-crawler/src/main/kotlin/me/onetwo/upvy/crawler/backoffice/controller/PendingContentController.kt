package me.onetwo.upvy.crawler.backoffice.controller

import me.onetwo.upvy.crawler.backoffice.domain.Category
import me.onetwo.upvy.crawler.backoffice.domain.PendingContentStatus
import me.onetwo.upvy.crawler.backoffice.service.ContentPublishService
import me.onetwo.upvy.crawler.backoffice.service.PendingContentService
import me.onetwo.upvy.crawler.domain.Difficulty
import me.onetwo.upvy.crawler.service.S3Service
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.security.Principal
import java.time.Duration

@Controller
@RequestMapping("/backoffice/pending")
class PendingContentController(
    private val pendingContentService: PendingContentService,
    private val contentPublishService: ContentPublishService,
    private val s3Service: S3Service
) {

    @GetMapping
    fun listPending(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        model: Model
    ): String {
        val contents = pendingContentService.getPendingContents(PageRequest.of(page, size))

        model.addAttribute("contents", contents)
        model.addAttribute("currentPage", page)
        model.addAttribute("categories", Category.entries)

        return "backoffice/pending/list"
    }

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long, model: Model): String {
        val content = pendingContentService.getById(id)
            ?: return "redirect:/backoffice/pending?error=notfound"

        // S3 Presigned URL 생성 (1시간 유효)
        val videoUrl = s3Service.generatePresignedUrl(
            s3Key = content.videoS3Key,
            expiration = Duration.ofHours(1)
        )
        val thumbnailUrl = content.thumbnailS3Key?.let {
            s3Service.generatePresignedUrl(s3Key = it, expiration = Duration.ofHours(1))
        }

        model.addAttribute("content", content)
        model.addAttribute("videoUrl", videoUrl)
        model.addAttribute("thumbnailUrl", thumbnailUrl)
        model.addAttribute("categories", Category.entries)
        model.addAttribute("difficulties", Difficulty.entries)
        model.addAttribute("tags", content.getTagsList())

        return "backoffice/pending/detail"
    }

    @PostMapping("/{id}/approve")
    fun approve(
        @PathVariable id: Long,
        principal: Principal,
        redirectAttributes: RedirectAttributes
    ): String {
        return try {
            // 백엔드 contents 테이블에 INSERT
            val publishedContentId = contentPublishService.publishContent(id)

            // pending_contents 상태 업데이트
            pendingContentService.approve(id, principal.name, publishedContentId)

            redirectAttributes.addFlashAttribute("successMessage", "콘텐츠가 승인되었습니다.")
            "redirect:/backoffice/pending"
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("errorMessage", "승인 실패: ${e.message}")
            "redirect:/backoffice/pending/$id"
        }
    }

    @PostMapping("/{id}/reject")
    fun reject(
        @PathVariable id: Long,
        @RequestParam reason: String,
        principal: Principal,
        redirectAttributes: RedirectAttributes
    ): String {
        return try {
            pendingContentService.reject(id, principal.name, reason)

            redirectAttributes.addFlashAttribute("successMessage", "콘텐츠가 거절되었습니다.")
            "redirect:/backoffice/pending"
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("errorMessage", "거절 실패: ${e.message}")
            "redirect:/backoffice/pending/$id"
        }
    }

    @PostMapping("/{id}/update")
    fun updateMetadata(
        @PathVariable id: Long,
        @RequestParam title: String,
        @RequestParam(required = false) description: String?,
        @RequestParam category: Category,
        @RequestParam(required = false) difficulty: Difficulty?,
        @RequestParam(required = false) tags: String?,
        principal: Principal,
        redirectAttributes: RedirectAttributes
    ): String {
        return try {
            val tagList = tags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

            pendingContentService.updateMetadata(
                id, title, description, category, difficulty, tagList, principal.name
            )

            redirectAttributes.addFlashAttribute("successMessage", "메타데이터가 수정되었습니다.")
            "redirect:/backoffice/pending/$id"
        } catch (e: Exception) {
            redirectAttributes.addFlashAttribute("errorMessage", "수정 실패: ${e.message}")
            "redirect:/backoffice/pending/$id"
        }
    }

    @GetMapping("/approved")
    fun listApproved(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        model: Model
    ): String {
        val contents = pendingContentService.getContentsByStatus(
            PendingContentStatus.APPROVED,
            PageRequest.of(page, size)
        )

        model.addAttribute("contents", contents)
        model.addAttribute("currentPage", page)
        model.addAttribute("status", "approved")

        return "backoffice/pending/history"
    }

    @GetMapping("/rejected")
    fun listRejected(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        model: Model
    ): String {
        val contents = pendingContentService.getContentsByStatus(
            PendingContentStatus.REJECTED,
            PageRequest.of(page, size)
        )

        model.addAttribute("contents", contents)
        model.addAttribute("currentPage", page)
        model.addAttribute("status", "rejected")

        return "backoffice/pending/history"
    }
}
