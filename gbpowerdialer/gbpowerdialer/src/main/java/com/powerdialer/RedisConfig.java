package com.powerdialer;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.powerdialer.util.Constants;

@Configuration
public class RedisConfig {

  @Bean
  public RedissonClient redissonClient(@Value("${redis.configuration.type}") String redisConfigurationType,
                                       @Value("${redis.addresses}") String[] redisAddresses,
                                       @Value("${redis.password}") String redisPassword) {
    Config config = new Config();
    if (Constants.REDIS_CONFIGURATION_TYPE_CLUSTER.equals(redisConfigurationType)) {
      config.useClusterServers()
          .setPingConnectionInterval(60000)
          .setKeepAlive(true)
          .setSubscriptionsPerConnection(100)
          .setSubscriptionConnectionPoolSize(200)
          .setReadMode(ReadMode.MASTER_SLAVE)
          .addNodeAddress(redisAddresses)
          .setPassword(redisPassword);
    } else {
      config.useSingleServer()
          .setPingConnectionInterval(60000)
          .setKeepAlive(true)
          .setSubscriptionsPerConnection(100)
          .setSubscriptionConnectionPoolSize(200)
          .setAddress(redisAddresses[0])
          .setPassword(redisPassword);
    }
    config.setCodec(StringCodec.INSTANCE);
    return Redisson.create(config);
  }

}
