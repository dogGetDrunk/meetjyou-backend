package com.dogGetDrunk.meetjyou

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@ConfigurationPropertiesScan
@SpringBootApplication
class MeetjyouApplication

fun main(args: Array<String>) {
    runApplication<MeetjyouApplication>(*args)
}
