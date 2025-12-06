package me.onetwo.growsnap.crawler.backoffice.config

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

@ControllerAdvice
class ThymeleafConfig {

    @ModelAttribute("currentUri")
    fun currentUri(request: HttpServletRequest): String {
        return request.requestURI
    }
}
