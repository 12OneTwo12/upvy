package me.onetwo.growsnap
import me.onetwo.growsnap.infrastructure.config.AbstractIntegrationTest

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class GrowSnapApplicationTests : AbstractIntegrationTest() {

    @Test
    fun contextLoads() {
    }

}
