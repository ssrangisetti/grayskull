package com.flipkart.grayskull;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@ComponentScan
@EnableMongoRepositories
@EnableMongoAuditing
@ConfigurationPropertiesScan
public class GrayskullConfiguration {
}
