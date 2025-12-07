package me.onetwo.growsnap.crawler.backoffice.controller

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
@RequestMapping("/backoffice")
class LoginController {

    @GetMapping("/login")
    fun loginPage(
        @RequestParam(required = false) error: Boolean?,
        @RequestParam(required = false) logout: Boolean?,
        model: Model
    ): String {
        if (error == true) {
            model.addAttribute("errorMessage", "아이디 또는 비밀번호가 올바르지 않습니다.")
        }
        if (logout == true) {
            model.addAttribute("logoutMessage", "로그아웃되었습니다.")
        }
        return "backoffice/login"
    }
}
