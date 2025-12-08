package me.onetwo.upvy
import me.onetwo.upvy.infrastructure.config.AbstractIntegrationTest

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class UpvyApplicationTests : AbstractIntegrationTest() {

    @Test
    fun contextLoads() {
    }

}
