package com.study.redis_test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class RedisTestApplication {

  public static void main(String[] args) {
    SpringApplication.run(RedisTestApplication.class, args);
  }

}
