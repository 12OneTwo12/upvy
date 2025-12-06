package me.onetwo.growsnap.crawler.backoffice.controller

import me.onetwo.growsnap.crawler.backoffice.service.PendingContentService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import java.security.Principal

@Controller
@RequestMapping("/backoffice")
class DashboardController(
    private val pendingContentService: PendingContentService
) {

    @GetMapping
    fun redirectToDashboard(): String {
        return "redirect:/backoffice/dashboard"
    }

    @GetMapping("/dashboard")
    fun dashboard(model: Model, principal: Principal): String {
        val stats = pendingContentService.getDashboardStats()
        val categoryStats = pendingContentService.getCategoryStats()

        model.addAttribute("stats", stats)
        model.addAttribute("categoryStats", categoryStats)
        model.addAttribute("username", principal.name)

        return "backoffice/dashboard"
    }
}
