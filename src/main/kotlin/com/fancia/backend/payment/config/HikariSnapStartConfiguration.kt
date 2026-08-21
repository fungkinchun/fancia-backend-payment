package com.fancia.backend.payment.config

import org.crac.Core
import org.crac.Resource
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.jdbc.HikariCheckpointRestoreLifecycle
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
@ConditionalOnClass(
    name = [
        "org.crac.Core",
        "com.zaxxer.hikari.HikariDataSource",
        "org.springframework.boot.jdbc.HikariCheckpointRestoreLifecycle"
    ]
)
class HikariSnapStartConfiguration {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    @ConditionalOnMissingBean
    fun hikariCheckpointRestoreLifecycle(
        dataSource: DataSource,
        applicationContext: ConfigurableApplicationContext
    ): HikariCheckpointRestoreLifecycle {
        log.info("Configuring HikariCheckpointRestoreLifecycle for SnapStart")
        val lifecycle = HikariCheckpointRestoreLifecycle(dataSource, applicationContext)
        Core.getGlobalContext().register(HikariPoolCracResource(lifecycle))
        log.info("Registered Hikari CRaC resource for SnapStart")
        return lifecycle
    }

    private class HikariPoolCracResource(
        private val lifecycle: HikariCheckpointRestoreLifecycle
    ) : Resource {
        private val log = LoggerFactory.getLogger(javaClass)
        override fun beforeCheckpoint(context: org.crac.Context<out Resource>) {
            log.info("SnapStart beforeCheckpoint: suspending Hikari pool")
            lifecycle.stop()
        }

        override fun afterRestore(context: org.crac.Context<out Resource>) {
            log.info("SnapStart afterRestore: resuming Hikari pool")
            lifecycle.start()
        }
    }
}
