package me.onetwo.upvy.crawler.backoffice.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { auth ->
                auth
                    // 정적 리소스 허용
                    .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                    // 로그인 페이지 허용
                    .requestMatchers("/backoffice/login", "/backoffice/login-error").permitAll()
                    // Actuator 엔드포인트 허용
                    .requestMatchers("/actuator/**").permitAll()
                    // 백오피스는 인증 필요
                    .requestMatchers("/backoffice/**").authenticated()
                    // 나머지는 허용 (배치 API 등)
                    .anyRequest().permitAll()
            }
            .formLogin { form ->
                form
                    .loginPage("/backoffice/login")
                    .loginProcessingUrl("/backoffice/login")
                    .defaultSuccessUrl("/backoffice/dashboard", true)
                    .failureUrl("/backoffice/login?error=true")
                    .usernameParameter("username")
                    .passwordParameter("password")
                    .permitAll()
            }
            .logout { logout ->
                logout
                    .logoutUrl("/backoffice/logout")
                    .logoutSuccessUrl("/backoffice/login?logout=true")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID")
                    .permitAll()
            }
            .sessionManagement { session ->
                session
                    .maximumSessions(1)
                    .maxSessionsPreventsLogin(false)
            }
            // CSRF는 Thymeleaf가 자동 처리
            .csrf { csrf ->
                csrf.ignoringRequestMatchers("/actuator/**")
            }

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }
}
